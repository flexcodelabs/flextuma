package com.flexcodelabs.flextuma.modules.whatsapp.services.webhook;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppInboxMessageRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.MESSAGES;

/** Stores inbound customer messages (and their media) in the owner's WhatsApp Inbox. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppInboundMessageIngestor {
    private static final Set<String> MEDIA_MESSAGE_TYPES = Set.of("image", "document", "audio", "video", "sticker");

    private final WhatsAppInboxMessageRepository inboxMessageRepository;
    private final WhatsAppMediaService mediaService;

    public void ingest(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        for (Map<String, Object> change : WhatsAppPayloads.changes(payload)) {
            Map<String, Object> value = WhatsAppPayloads.value(change);
            Map<String, String> contactNames = contactNamesByWaId(value);
            WhatsAppPayloads.mapList(value, MESSAGES).forEach(raw -> saveInboundMessage(config, raw, contactNames));
        }
    }

    private Map<String, String> contactNamesByWaId(Map<String, Object> value) {
        Map<String, String> names = new HashMap<>();
        for (Map<String, Object> contact : WhatsAppPayloads.mapList(value, "contacts")) {
            Object waId = contact.get("wa_id");
            Object name = WhatsAppPayloads.nestedMap(contact, "profile").get("name");
            if (waId != null && name != null) {
                names.put(waId.toString(), name.toString());
            }
        }
        return names;
    }

    private void saveInboundMessage(WhatsAppWebhookConfig config, Map<String, Object> raw, Map<String, String> contactNames) {
        Object idObj = raw.get("id");
        Object fromObj = raw.get("from");
        if (idObj == null || fromObj == null || inboxMessageRepository.existsByProviderMessageId(idObj.toString())) {
            return;
        }
        String type = WhatsAppPayloads.textOr(raw.get("type"), "unknown");
        WhatsAppInboxMessage message = new WhatsAppInboxMessage();
        message.setConfig(config);
        message.setCreatedBy(config.getCreatedBy());
        message.setFromNumber(fromObj.toString());
        message.setContactName(contactNames.get(fromObj.toString()));
        message.setProviderMessageId(idObj.toString());
        message.setMessageType(type);
        message.setReceivedAt(parseTimestamp(raw.get("timestamp")));
        if (MEDIA_MESSAGE_TYPES.contains(type)) {
            attachMedia(message, config, WhatsAppPayloads.nestedMap(raw, type));
        } else {
            message.setContent(extractInboundContent(raw, type));
        }
        persist(message);
    }

    private void persist(WhatsAppInboxMessage message) {
        try {
            inboxMessageRepository.save(message);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate WhatsApp inbound message [{}] ignored", message.getProviderMessageId());
        }
    }

    private void attachMedia(WhatsAppInboxMessage message, WhatsAppWebhookConfig config, Map<String, Object> media) {
        Object mediaId = media.get("id");
        String caption = WhatsAppPayloads.textOr(media.get("caption"), null);
        message.setContent(caption);
        if (mediaId == null) {
            return;
        }
        message.setMediaId(mediaId.toString());
        message.setMimeType(WhatsAppPayloads.textOr(media.get("mime_type"), null));
        message.setCaption(caption);
        mediaService.download(config, mediaId.toString()).ifPresent(downloaded -> {
            message.setMediaPath(downloaded.path());
            message.setMediaSize(downloaded.size());
        });
    }

    private String extractInboundContent(Map<String, Object> raw, String type) {
        Object body = "text".equals(type) ? WhatsAppPayloads.nestedMap(raw, "text").get("body") : null;
        return body != null ? body.toString() : "[" + type + "]";
    }

    private LocalDateTime parseTimestamp(Object timestamp) {
        try {
            return LocalDateTime.ofEpochSecond(Long.parseLong(String.valueOf(timestamp)), 0, ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            return LocalDateTime.now();
        }
    }
}
