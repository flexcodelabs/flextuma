package com.flexcodelabs.flextuma.modules.whatsapp.services.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookEvent;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.MESSAGES;
import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.STATUS;
import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.STATUSES;
import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.TEMPLATE_STATUS_FIELD;

/** Persists every change of a webhook delivery to the event log (one row per change). */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppWebhookEventRecorder {
    private static final int SUMMARY_MAX_LENGTH = 500;

    private final WhatsAppWebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /** Best-effort: a logging failure must never stop the delivery from being processed or acknowledged. */
    public void record(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        for (Map<String, Object> change : WhatsAppPayloads.changes(payload)) {
            try {
                eventRepository.save(toEvent(config, change));
            } catch (JsonProcessingException | RuntimeException e) {
                log.warn("Unable to record WhatsApp webhook event for config [{}]: {}", config.getId(), e.getMessage());
            }
        }
    }

    private WhatsAppWebhookEvent toEvent(WhatsAppWebhookConfig config, Map<String, Object> change) throws JsonProcessingException {
        Map<String, Object> value = WhatsAppPayloads.value(change);
        String field = fieldOf(change, value);
        WhatsAppWebhookEvent event = new WhatsAppWebhookEvent();
        event.setConfig(config);
        event.setCreatedBy(config.getCreatedBy());
        event.setField(field);
        event.setEventType(eventType(field, value));
        event.setSummary(WhatsAppPayloads.truncate(eventSummary(field, value), SUMMARY_MAX_LENGTH));
        event.setPayload(objectMapper.writeValueAsString(change));
        event.setReceivedAt(LocalDateTime.now());
        return event;
    }

    private String fieldOf(Map<String, Object> change, Map<String, Object> value) {
        if (change.get("field") != null) {
            return change.get("field").toString();
        }
        return value.containsKey(MESSAGES) || value.containsKey(STATUSES) ? MESSAGES : "unknown";
    }

    String eventType(String field, Map<String, Object> value) {
        if (MESSAGES.equals(field)) {
            return messagesEventType(value, field);
        }
        if (TEMPLATE_STATUS_FIELD.equals(field) && value.get("event") != null) {
            return "template." + value.get("event");
        }
        return field;
    }

    private String messagesEventType(Map<String, Object> value, String field) {
        if (value.get(MESSAGES) instanceof List<?>) {
            return "message.received";
        }
        Object status = WhatsAppPayloads.firstOf(value, STATUSES).get(STATUS);
        if (status != null) {
            return "status." + status;
        }
        return value.get("errors") instanceof List<?> ? "message.error" : field;
    }

    String eventSummary(String field, Map<String, Object> value) {
        if (MESSAGES.equals(field)) {
            return messagesSummary(value, field);
        }
        if (TEMPLATE_STATUS_FIELD.equals(field)) {
            return templateSummary(value);
        }
        return field;
    }

    private String messagesSummary(Map<String, Object> value, String field) {
        List<Map<String, Object>> messages = WhatsAppPayloads.mapList(value, MESSAGES);
        if (!messages.isEmpty()) {
            Map<String, Object> first = messages.get(0);
            String summary = "Inbound " + first.getOrDefault("type", "message") + " from " + WhatsAppPayloads.maskPhone(first.get("from"));
            return messages.size() > 1 ? summary + " (+" + (messages.size() - 1) + " more)" : summary;
        }
        Map<String, Object> status = WhatsAppPayloads.firstOf(value, STATUSES);
        return status.get(STATUS) != null ? statusSummary(status) : field;
    }

    private String statusSummary(Map<String, Object> status) {
        String error = WhatsAppPayloads.extractErrorMessage(status);
        String summary = "Message to " + WhatsAppPayloads.maskPhone(status.get("recipient_id")) + " " + status.get(STATUS);
        return error != null ? summary + ": " + error : summary;
    }

    private String templateSummary(Map<String, Object> value) {
        String summary = value.get("message_template_name") + " (" + value.get("message_template_language") + ") " + value.get("event");
        Object reason = value.get("reason");
        return reason != null && !"NONE".equals(reason.toString()) ? summary + ": " + reason : summary;
    }
}
