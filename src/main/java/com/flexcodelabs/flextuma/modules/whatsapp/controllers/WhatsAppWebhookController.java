package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppMetaAppRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppSharedWebhookRouter;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppWebhookProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Meta Cloud API webhook endpoints. Events are processed for the owning configuration and queued
 * for relay to its callback URL. Three URL shapes are accepted:
 * <ul>
 *   <li>{@code /{callbackToken}} -- one configuration (one phone number);</li>
 *   <li>{@code /apps/{callbackToken}} -- a Meta app's shared URL, routed per phone number / WABA;</li>
 *   <li>the bare path -- legacy, resolved by verify token and phone_number_id.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {
    private static final String SUBSCRIBE_MODE = "subscribe";
    private static final String MODE_PARAM = "hub.mode";
    private static final String VERIFY_TOKEN_PARAM = "hub.verify_token";
    private static final String CHALLENGE_PARAM = "hub.challenge";
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final WhatsAppWebhookConfigRepository configRepository;
    private final WhatsAppMetaAppRepository metaAppRepository;
    private final WhatsAppWebhookProcessor processor;
    private final WhatsAppSharedWebhookRouter sharedRouter;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(MODE_PARAM) String mode,
            @RequestParam(VERIFY_TOKEN_PARAM) String verifyToken, @RequestParam(CHALLENGE_PARAM) String challenge) {
        Optional<WhatsAppWebhookConfig> config = configRepository.findByVerifyTokenAndActiveTrue(verifyToken);
        if (!SUBSCRIBE_MODE.equals(mode) || config.isEmpty()) {
            return forbidden();
        }
        markVerified(config.get());
        return ResponseEntity.ok(challenge);
    }

    @GetMapping("/{callbackToken}")
    public ResponseEntity<String> verifyGeneratedCallback(@PathVariable String callbackToken,
            @RequestParam(MODE_PARAM) String mode, @RequestParam(VERIFY_TOKEN_PARAM) String verifyToken,
            @RequestParam(CHALLENGE_PARAM) String challenge) {
        Optional<WhatsAppWebhookConfig> config = configRepository.findByCallbackTokenAndActiveTrue(callbackToken)
                .filter(found -> isSubscription(mode, found.getVerifyToken(), verifyToken));
        if (config.isEmpty()) {
            return forbidden();
        }
        markVerified(config.get());
        return ResponseEntity.ok(challenge);
    }

    @GetMapping("/apps/{callbackToken}")
    public ResponseEntity<String> verifySharedCallback(@PathVariable String callbackToken,
            @RequestParam(MODE_PARAM) String mode, @RequestParam(VERIFY_TOKEN_PARAM) String verifyToken,
            @RequestParam(CHALLENGE_PARAM) String challenge) {
        Optional<WhatsAppMetaApp> app = metaAppRepository.findByCallbackTokenAndActiveTrue(callbackToken)
                .filter(found -> isSubscription(mode, found.getVerifyToken(), verifyToken));
        if (app.isEmpty()) {
            return forbidden();
        }
        app.get().setLastVerifiedAt(LocalDateTime.now());
        metaAppRepository.save(app.get());
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawPayload,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        Optional<Map<String, Object>> payload = parse(rawPayload);
        if (payload.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<WhatsAppWebhookConfig> config = WhatsAppPayloads.phoneNumberIdOfPayload(payload.get())
                .flatMap(configRepository::findByPhoneNumberIdAndActiveTrue);
        return handle(payload.get(), rawPayload, signature, config);
    }

    /** The callback token already scopes this request to exactly one config (it's unique and
     * unguessable), and the Meta signature is the real authenticity check. Meta delivers many event
     * types (template status, account alerts, etc.) with no metadata.phone_number_id at all, so
     * none is required here. */
    @PostMapping("/{callbackToken}")
    public ResponseEntity<Void> receiveGeneratedCallback(@PathVariable String callbackToken, @RequestBody String rawPayload,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        Optional<Map<String, Object>> payload = parse(rawPayload);
        if (payload.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return handle(payload.get(), rawPayload, signature, configRepository.findByCallbackTokenAndActiveTrue(callbackToken));
    }

    @PostMapping("/apps/{callbackToken}")
    public ResponseEntity<Void> receiveSharedCallback(@PathVariable String callbackToken, @RequestBody String rawPayload,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        Optional<Map<String, Object>> payload = parse(rawPayload);
        if (payload.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<WhatsAppMetaApp> app = metaAppRepository.findByCallbackTokenAndActiveTrue(callbackToken);
        if (app.isEmpty()) {
            log.warn("Ignoring shared WhatsApp webhook with no active Meta app");
            return ResponseEntity.ok().build();
        }
        if (!processor.validSignature(app.get(), rawPayload, signature)) {
            log.warn("Rejecting shared WhatsApp webhook with an invalid Meta signature for app [{}]", app.get().getId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        app.get().setLastEventAt(LocalDateTime.now());
        metaAppRepository.save(app.get());
        sharedRouter.route(app.get(), payload.get()).forEach(routed -> processor.process(routed.config(), routed.payload()));
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> handle(Map<String, Object> payload, String rawPayload, String signature,
            Optional<WhatsAppWebhookConfig> config) {
        if (config.isEmpty()) {
            log.warn("Ignoring WhatsApp webhook with no active configuration");
            return ResponseEntity.ok().build();
        }
        if (!processor.validSignature(config.get(), rawPayload, signature)) {
            log.warn("Rejecting WhatsApp webhook with an invalid Meta signature for config [{}]", config.get().getId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        processor.process(config.get(), payload);
        return ResponseEntity.ok().build();
    }

    private Optional<Map<String, Object>> parse(String rawPayload) {
        try {
            return Optional.ofNullable(objectMapper.readValue(rawPayload, PAYLOAD_TYPE));
        } catch (JsonProcessingException e) {
            log.warn("Rejecting WhatsApp webhook with unparsable JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isSubscription(String mode, String expectedToken, String verifyToken) {
        return SUBSCRIBE_MODE.equals(mode) && expectedToken != null && MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8), verifyToken.getBytes(StandardCharsets.UTF_8));
    }

    private void markVerified(WhatsAppWebhookConfig config) {
        config.setLastVerifiedAt(LocalDateTime.now());
        configRepository.save(config);
    }

    private static ResponseEntity<String> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
