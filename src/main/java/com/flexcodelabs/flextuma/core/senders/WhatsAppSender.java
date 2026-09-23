package com.flexcodelabs.flextuma.core.senders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import com.flexcodelabs.flextuma.core.services.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** WhatsApp Cloud API text-message sender. The connector key is a Meta access token. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppSender implements SmsSender {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getProvider() {
        return "WHATSAPP";
    }

    @Override
    public SmsSendResult sendSms(SmsConnector config, String to, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getKey());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", normaliseRecipient(to));
            body.put("type", "text");
            body.put("text", Map.of("body", message));

            ResponseEntity<Map> response = restTemplate.postForEntity(messageUrl(config),
                    new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> responseBody = objectMapper.convertValue(response.getBody(), new TypeReference<>() {});
            String messageId = extractMessageId(responseBody);
            if (response.getStatusCode().is2xxSuccessful() && messageId != null) {
                return SmsSendResult.success("WhatsApp message accepted", messageId, responseBody);
            }
            return SmsSendResult.failure("WhatsApp API did not return a message id",
                    String.valueOf(response.getStatusCode().value()), responseBody);
        } catch (Exception e) {
            return SmsSendResult.failure("Failed to send WhatsApp message: " + e.getMessage(), "SEND_ERROR",
                    Map.of("error", e.getMessage()));
        }
    }

    /** Sends a Meta-approved WhatsApp Business template message (type: "template"), the only kind
     * of business-initiated message Meta accepts outside the 24h customer-service window. This is
     * intentionally not on the shared {@link SmsSender} interface: no other provider has an
     * equivalent concept, and the caller (WhatsAppTemplateSendService-style code) already knows
     * it's talking to WhatsApp specifically. {@code components} is the raw Meta components array
     * (header/body text or media parameters, dynamic-URL button parameters) -- passed through
     * as-is, not built here. */
    public SmsSendResult sendTemplate(SmsConnector config, String to, String templateName, String languageCode,
            List<Map<String, Object>> components) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getKey());

            Map<String, Object> template = new LinkedHashMap<>();
            template.put("name", templateName);
            template.put("language", Map.of("code", languageCode));
            if (components != null && !components.isEmpty()) {
                template.put("components", components);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", normaliseRecipient(to));
            body.put("type", "template");
            body.put("template", template);

            ResponseEntity<Map> response = restTemplate.postForEntity(messageUrl(config),
                    new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> responseBody = objectMapper.convertValue(response.getBody(), new TypeReference<>() {});
            String messageId = extractMessageId(responseBody);
            if (response.getStatusCode().is2xxSuccessful() && messageId != null) {
                return SmsSendResult.success("WhatsApp template message accepted", messageId, responseBody);
            }
            return SmsSendResult.failure("WhatsApp API did not return a message id",
                    String.valueOf(response.getStatusCode().value()), responseBody);
        } catch (Exception e) {
            return SmsSendResult.failure("Failed to send WhatsApp template message: " + e.getMessage(), "SEND_ERROR",
                    Map.of("error", e.getMessage()));
        }
    }

    /** Tells Meta a message was read, so the sender sees blue double-ticks. Never throws --
     * this is best-effort: Meta's API hiccuping shouldn't block marking a message read locally. */
    public boolean markAsRead(SmsConnector config, String providerMessageId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getKey());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("status", "read");
            body.put("message_id", providerMessageId);

            ResponseEntity<Map> response = restTemplate.postForEntity(messageUrl(config),
                    new HttpEntity<>(body, headers), Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp read receipt for message [{}]: {}", providerMessageId, e.getMessage());
            return false;
        }
    }

    private String messageUrl(SmsConnector config) {
        String base = config.getUrl().replaceAll("/$", "");
        if (base.contains("{phoneNumberId}")) {
            return base.replace("{phoneNumberId}", config.getSenderId());
        }
        return base + "/" + config.getSenderId() + "/messages";
    }

    private String normaliseRecipient(String recipient) {
        return recipient == null ? null : recipient.replaceAll("[^0-9]", "");
    }

    @SuppressWarnings("unchecked")
    private String extractMessageId(Map<String, Object> body) {
        if (body == null || !(body.get("messages") instanceof java.util.List<?> messages) || messages.isEmpty()
                || !(messages.get(0) instanceof Map<?, ?> message)) {
            return null;
        }
        Object id = message.get("id");
        return id == null ? null : id.toString();
    }
}
