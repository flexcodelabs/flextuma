package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Meta Cloud API webhook endpoint. Events are relayed unchanged to the owning user's callback URL. */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {
    private final WhatsAppWebhookConfigRepository configRepository;
    private final SmsLogRepository smsLogRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && configRepository.findByVerifyTokenAndActiveTrue(verifyToken).isPresent()) return ResponseEntity.ok(challenge);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/{callbackToken}")
    public ResponseEntity<String> verifyGeneratedCallback(@PathVariable String callbackToken,
            @RequestParam("hub.mode") String mode, @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        Optional<WhatsAppWebhookConfig> config = configRepository.findByCallbackTokenAndActiveTrue(callbackToken);
        if (config.isPresent() && "subscribe".equals(mode) && MessageDigest.isEqual(
                config.get().getVerifyToken().getBytes(StandardCharsets.UTF_8), verifyToken.getBytes(StandardCharsets.UTF_8))) return ResponseEntity.ok(challenge);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawPayload, Map.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        return handle(payload, rawPayload, signature, phoneNumberId(payload).flatMap(configRepository::findByPhoneNumberIdAndActiveTrue));
    }

    @PostMapping("/{callbackToken}")
    public ResponseEntity<Void> receiveGeneratedCallback(@PathVariable String callbackToken, @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        try {
            Map<String, Object> payload = objectMapper.readValue(rawPayload, Map.class);
            Optional<WhatsAppWebhookConfig> config = configRepository.findByCallbackTokenAndActiveTrue(callbackToken);
            if (config.isPresent() && !config.get().getPhoneNumberId().equals(phoneNumberId(payload).orElse(null))) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            return handle(payload, rawPayload, signature, config);
        } catch (Exception e) { return ResponseEntity.badRequest().build(); }
    }

    private ResponseEntity<Void> handle(Map<String, Object> payload, String rawPayload, String signature, Optional<WhatsAppWebhookConfig> config) {
        if (config.isEmpty()) { log.warn("Ignoring WhatsApp webhook with no active configuration"); return ResponseEntity.ok().build(); }
        if (!validMetaSignature(config.get(), rawPayload, signature)) { log.warn("Rejecting WhatsApp webhook with an invalid Meta signature for config [{}]", config.get().getId()); return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); }
        updateDeliveryStatus(payload); relay(config.get(), payload); return ResponseEntity.ok().build();
    }

    private Optional<String> phoneNumberId(Map<String, Object> payload) {
        return changes(payload).stream().map(change -> nestedMap(change, "value"))
                .map(value -> nestedMap(value, "metadata")).map(metadata -> metadata.get("phone_number_id"))
                .filter(value -> value != null && !value.toString().isBlank()).map(Object::toString).findFirst();
    }

    private void updateDeliveryStatus(Map<String, Object> payload) {
        for (Map<String, Object> change : changes(payload)) {
            Object statuses = nestedMap(change, "value").get("statuses");
            if (!(statuses instanceof List<?> list)) continue;
            for (Object status : list) {
                if (!(status instanceof Map<?, ?> raw)) continue;
                Object id = raw.get("id"), value = raw.get("status");
                if (id != null && value != null) smsLogRepository.findByProviderMessageId(id.toString()).ifPresent(log -> applyStatus(log, value.toString()));
            }
        }
    }

    private void applyStatus(SmsLog logEntry, String status) {
        if ("delivered".equalsIgnoreCase(status) || "read".equalsIgnoreCase(status)) logEntry.setStatus(SmsLogStatus.DELIVERED);
        else if ("failed".equalsIgnoreCase(status)) logEntry.setStatus(SmsLogStatus.FAILED);
        else if ("sent".equalsIgnoreCase(status)) logEntry.setStatus(SmsLogStatus.SENT);
        else return;
        smsLogRepository.save(logEntry);
    }

    private void relay(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.set("X-Flextuma-Event", "whatsapp");
            if (config.getSigningSecret() != null && !config.getSigningSecret().isBlank()) headers.set("X-Flextuma-Signature-256", "sha256=" + hmac(json, config.getSigningSecret()));
            restTemplate.postForEntity(config.getCallbackUrl(), new HttpEntity<>(json, headers), Void.class);
        } catch (Exception e) { log.warn("Unable to relay WhatsApp webhook for config [{}]: {}", config.getId(), e.getMessage()); }
    }

    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder result = new StringBuilder(); for (byte b : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) result.append(String.format("%02x", b)); return result.toString();
    }

    private boolean validMetaSignature(WhatsAppWebhookConfig config, String rawPayload, String signature) {
        if (config.getAppSecret() == null || config.getAppSecret().isBlank()) return true;
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            String expected = "sha256=" + hmac(rawPayload, config.getAppSecret());
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) { return false; }
    }

    @SuppressWarnings("unchecked") private Map<String, Object> nestedMap(Map<String, Object> source, String key) { Object value = source.get(key); return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> changes(Map<String, Object> payload) {
        Object entries = payload.get("entry"); if (!(entries instanceof List<?> entryList)) return List.of();
        return entryList.stream().filter(Map.class::isInstance).flatMap(entry -> { Object values = ((Map<String, Object>) entry).get("changes"); return values instanceof List<?> list ? list.stream() : java.util.stream.Stream.empty(); }).filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }
}
