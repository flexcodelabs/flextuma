package com.flexcodelabs.flextuma.core.senders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import com.flexcodelabs.flextuma.core.services.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** WhatsApp Cloud API text-message sender. The connector key is a Meta access token. */
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
