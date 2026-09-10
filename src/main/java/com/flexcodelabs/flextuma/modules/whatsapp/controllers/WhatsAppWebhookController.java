package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.helpers.HmacUtil;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppInboxMessageRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Meta Cloud API webhook endpoint. Events are queued for relay to the owning user's callback URL. */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {
    private static final Set<String> MEDIA_MESSAGE_TYPES = Set.of("image", "document", "audio", "video", "sticker");

    private final WhatsAppWebhookConfigRepository configRepository;
    private final SmsLogRepository smsLogRepository;
    private final WhatsAppInboxMessageRepository inboxMessageRepository;
    private final WhatsAppRelayDeliveryRepository relayDeliveryRepository;
    private final WhatsAppMediaService mediaService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        Optional<WhatsAppWebhookConfig> config = configRepository.findByVerifyTokenAndActiveTrue(verifyToken);
        if ("subscribe".equals(mode) && config.isPresent()) { markVerified(config.get()); return ResponseEntity.ok(challenge); }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/{callbackToken}")
    public ResponseEntity<String> verifyGeneratedCallback(@PathVariable String callbackToken,
            @RequestParam("hub.mode") String mode, @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        Optional<WhatsAppWebhookConfig> config = configRepository.findByCallbackTokenAndActiveTrue(callbackToken);
        if (config.isPresent() && "subscribe".equals(mode) && MessageDigest.isEqual(
                config.get().getVerifyToken().getBytes(StandardCharsets.UTF_8), verifyToken.getBytes(StandardCharsets.UTF_8))) { markVerified(config.get()); return ResponseEntity.ok(challenge); }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private void markVerified(WhatsAppWebhookConfig config) {
        config.setLastVerifiedAt(LocalDateTime.now());
        configRepository.save(config);
    }

    private void markEventReceived(WhatsAppWebhookConfig config) {
        config.setLastEventAt(LocalDateTime.now());
        configRepository.save(config);
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawPayload, Map.class);
        } catch (Exception e) {
            log.warn("Rejecting WhatsApp webhook with unparsable JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        try {
            return handle(payload, rawPayload, signature, phoneNumberId(payload).flatMap(configRepository::findByPhoneNumberIdAndActiveTrue));
        } catch (RuntimeException e) {
            log.error("WhatsApp webhook processing failed", e);
            throw e;
        }
    }

    @PostMapping("/{callbackToken}")
    public ResponseEntity<Void> receiveGeneratedCallback(@PathVariable String callbackToken, @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        try {
            Map<String, Object> payload = objectMapper.readValue(rawPayload, Map.class);
            // The callback token itself already scopes this request to exactly one config (it's
            // unique and unguessable), and validMetaSignature() is the real authenticity check.
            // Meta delivers many event types (template status, account alerts, etc.) that carry no
            // metadata.phone_number_id at all, so requiring one here rejected legitimate events.
            Optional<WhatsAppWebhookConfig> config = configRepository.findByCallbackTokenAndActiveTrue(callbackToken);
            return handle(payload, rawPayload, signature, config);
        } catch (Exception e) {
            log.error("WhatsApp webhook processing failed for callback token [{}]", callbackToken, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private ResponseEntity<Void> handle(Map<String, Object> payload, String rawPayload, String signature, Optional<WhatsAppWebhookConfig> config) {
        if (config.isEmpty()) { log.warn("Ignoring WhatsApp webhook with no active configuration"); return ResponseEntity.ok().build(); }
        if (!validMetaSignature(config.get(), rawPayload, signature)) { log.warn("Rejecting WhatsApp webhook with an invalid Meta signature for config [{}]", config.get().getId()); return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); }
        markEventReceived(config.get());
        updateDeliveryStatus(payload); ingestInboundMessages(config.get(), payload); relay(config.get(), payload);
        log.info("Processed WhatsApp webhook for config [{}]: {} change(s)", config.get().getId(), changes(payload).size());
        return ResponseEntity.ok().build();
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
                if (id != null && value != null) smsLogRepository.findByProviderMessageId(id.toString()).ifPresent(log -> applyStatus(log, value.toString(), (Map<String, Object>) raw));
            }
        }
    }

    // sent < delivered < read: Meta's status callbacks can arrive out of order (or duplicated),
    // and without this a late "delivered" retry could visibly regress an already-READ message's
    // blue ticks back to gray. FAILED isn't in this progression -- it's a terminal outcome, not
    // a step past it.
    private static final List<SmsLogStatus> DELIVERY_PROGRESSION = List.of(SmsLogStatus.SENT, SmsLogStatus.DELIVERED, SmsLogStatus.READ);

    @SuppressWarnings("unchecked")
    private void applyStatus(SmsLog logEntry, String status, Map<String, Object> raw) {
        SmsLogStatus next;
        if ("read".equalsIgnoreCase(status)) next = SmsLogStatus.READ;
        else if ("delivered".equalsIgnoreCase(status)) next = SmsLogStatus.DELIVERED;
        else if ("failed".equalsIgnoreCase(status)) next = SmsLogStatus.FAILED;
        else if ("sent".equalsIgnoreCase(status)) next = SmsLogStatus.SENT;
        else return;

        if (next != SmsLogStatus.FAILED && isDeliveryRegression(logEntry.getStatus(), next)) return;

        logEntry.setStatus(next);
        if (next == SmsLogStatus.FAILED) logEntry.setError(extractErrorMessage(raw));
        smsLogRepository.save(logEntry);
    }

    private boolean isDeliveryRegression(SmsLogStatus current, SmsLogStatus next) {
        int currentIndex = DELIVERY_PROGRESSION.indexOf(current);
        int nextIndex = DELIVERY_PROGRESSION.indexOf(next);
        return currentIndex >= 0 && nextIndex >= 0 && nextIndex < currentIndex;
    }

    private String extractErrorMessage(Map<String, Object> raw) {
        Object errors = raw.get("errors");
        if (!(errors instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)) return null;
        Object message = first.get("message"), title = first.get("title"), code = first.get("code");
        String text = message != null ? message.toString() : title != null ? title.toString() : "WhatsApp delivery failed";
        return code != null ? "[" + code + "] " + text : text;
    }

    @SuppressWarnings("unchecked")
    private void ingestInboundMessages(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        for (Map<String, Object> change : changes(payload)) {
            Map<String, Object> value = nestedMap(change, "value");
            Object messages = value.get("messages");
            if (!(messages instanceof List<?> list)) continue;
            Map<String, String> contactNames = contactNamesByWaId(value);
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) continue;
                saveInboundMessage(config, (Map<String, Object>) raw, contactNames);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> contactNamesByWaId(Map<String, Object> value) {
        Object contacts = value.get("contacts");
        if (!(contacts instanceof List<?> list)) return Map.of();
        Map<String, String> names = new HashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Object waId = raw.get("wa_id");
            Object name = nestedMap((Map<String, Object>) raw, "profile").get("name");
            if (waId != null && name != null) names.put(waId.toString(), name.toString());
        }
        return names;
    }

    private void saveInboundMessage(WhatsAppWebhookConfig config, Map<String, Object> raw, Map<String, String> contactNames) {
        Object idObj = raw.get("id"), fromObj = raw.get("from"), typeObj = raw.get("type");
        if (idObj == null || fromObj == null) return;
        String providerMessageId = idObj.toString();
        if (inboxMessageRepository.existsByProviderMessageId(providerMessageId)) return;
        String type = typeObj != null ? typeObj.toString() : "unknown";
        WhatsAppInboxMessage message = new WhatsAppInboxMessage();
        message.setConfig(config);
        message.setCreatedBy(config.getCreatedBy());
        message.setFromNumber(fromObj.toString());
        message.setContactName(contactNames.get(fromObj.toString()));
        message.setProviderMessageId(providerMessageId);
        message.setMessageType(type);
        message.setReceivedAt(parseTimestamp(raw.get("timestamp")));

        if (MEDIA_MESSAGE_TYPES.contains(type)) {
            attachMedia(message, config, nestedMap(raw, type));
        } else {
            message.setContent(extractInboundContent(raw, type));
        }

        try {
            inboxMessageRepository.save(message);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate WhatsApp inbound message [{}] ignored", providerMessageId);
        }
    }

    private void attachMedia(WhatsAppInboxMessage message, WhatsAppWebhookConfig config, Map<String, Object> media) {
        Object mediaId = media.get("id"), mimeType = media.get("mime_type"), caption = media.get("caption");
        message.setContent(caption != null ? caption.toString() : null);
        if (mediaId == null) {
            return;
        }
        message.setMediaId(mediaId.toString());
        message.setMimeType(mimeType != null ? mimeType.toString() : null);
        message.setCaption(caption != null ? caption.toString() : null);
        mediaService.download(config, mediaId.toString()).ifPresent(message::setMediaPath);
    }

    private String extractInboundContent(Map<String, Object> raw, String type) {
        if ("text".equals(type)) {
            Object body = nestedMap(raw, "text").get("body");
            if (body != null) return body.toString();
        }
        return "[" + type + "]";
    }

    private LocalDateTime parseTimestamp(Object timestamp) {
        try {
            return LocalDateTime.ofEpochSecond(Long.parseLong(timestamp.toString()), 0, ZoneOffset.UTC);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private void relay(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        if (config.getCallbackUrl() == null || config.getCallbackUrl().isBlank()) return;
        try {
            WhatsAppRelayDelivery delivery = new WhatsAppRelayDelivery();
            delivery.setConfig(config);
            delivery.setCreatedBy(config.getCreatedBy());
            delivery.setPayload(objectMapper.writeValueAsString(payload));
            relayDeliveryRepository.save(delivery);
        } catch (Exception e) { log.warn("Unable to queue WhatsApp relay for config [{}]: {}", config.getId(), e.getMessage()); }
    }

    private boolean validMetaSignature(WhatsAppWebhookConfig config, String rawPayload, String signature) {
        if (config.getAppSecret() == null || config.getAppSecret().isBlank()) return true;
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            String expected = "sha256=" + HmacUtil.sha256Hex(rawPayload, config.getAppSecret());
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) { return false; }
    }

    @SuppressWarnings("unchecked") private Map<String, Object> nestedMap(Map<String, Object> source, String key) { Object value = source.get(key); return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> changes(Map<String, Object> payload) {
        Object entries = payload.get("entry"); if (!(entries instanceof List<?> entryList)) return List.of();
        return entryList.stream().filter(Map.class::isInstance).flatMap(entry -> { Object values = ((Map<String, Object>) entry).get("changes"); return values instanceof List<?> list ? list.stream() : java.util.stream.Stream.empty(); }).filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }
}
