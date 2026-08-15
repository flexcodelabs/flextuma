package com.flexcodelabs.flextuma.core.senders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import com.flexcodelabs.flextuma.core.services.SmsSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BeemSender implements SmsSender {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BeemSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProvider() {
        return "BEEM";
    }

    @Override
    public SmsSendResult sendSms(SmsConnector config, String to, String message) {
        try {
            ResponseEntity<BeemSmsResponse> response = makeApiCall(config, to, message);
            return processResponse(response, to);
        } catch (Exception e) {
            log.error("BEEM Error: {}", e.getMessage());
            Map<String, Object> errorResponse = extractErrorResponse(e);
            return SmsSendResult.failure(
                    "Failed to send via Beem: " + e.getMessage(),
                    "SEND_ERROR",
                    errorResponse);
        }
    }

    private ResponseEntity<BeemSmsResponse> makeApiCall(SmsConnector config, String to, String message) {
        HttpHeaders headers = createHeaders(config);
        BeemSmsRequest requestBody = createRequestBody(config, to, message);
        HttpEntity<BeemSmsRequest> entity = new HttpEntity<>(requestBody, headers);

        return restTemplate.postForEntity(
                config.getUrl(),
                entity,
                BeemSmsResponse.class);
    }

    private HttpHeaders createHeaders(SmsConnector config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = config.getKey() + ":" + config.getSecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);

        return headers;
    }

    private BeemSmsRequest createRequestBody(SmsConnector config, String to, String message) {
        BeemRequestSettings settings = getRequestSettings(config);
        BeemSmsRequest requestBody = new BeemSmsRequest();
        requestBody.setSourceAddr(config.getSenderId());
        requestBody.setMessage(message);
        requestBody.setScheduleTime(settings.scheduleTime());
        requestBody.setEncoding(settings.encoding());

        Recipient recipient = new Recipient();
        recipient.setDestAddr(to);
        recipient.setRecipientId("1");
        requestBody.setRecipients(Collections.singletonList(recipient));

        return requestBody;
    }

    /**
     * Beem's optional request settings can be supplied in a connector's
     * {@code extraSettings}, for example {@code {"encoding":"0","schedule_time":"2026-08-15 10:30"}}.
     */
    private BeemRequestSettings getRequestSettings(SmsConnector config) {
        if (config.getExtraSettings() == null || config.getExtraSettings().isBlank()) {
            return new BeemRequestSettings("0", "");
        }

        try {
            Map<String, Object> settings = objectMapper.readValue(config.getExtraSettings(),
                    new TypeReference<Map<String, Object>>() {
                    });
            return new BeemRequestSettings(
                    String.valueOf(settings.getOrDefault("encoding", "0")),
                    String.valueOf(settings.getOrDefault("schedule_time", "")));
        } catch (Exception e) {
            throw new IllegalArgumentException("BEEM extraSettings must be valid JSON", e);
        }
    }

    private SmsSendResult processResponse(ResponseEntity<BeemSmsResponse> response, String to) {
        BeemSmsResponse responseBody = response.getBody();
        Map<String, Object> responseMap = objectMapper.convertValue(responseBody,
                new TypeReference<Map<String, Object>>() {
                });

        if (response.getStatusCode().is2xxSuccessful()
                && (responseBody == null || !Boolean.FALSE.equals(responseBody.getSuccessful()))) {
            log.info("BEEM: SMS sent successfully to {}", to);
            return SmsSendResult.success(
                    responseBody != null && responseBody.getMessage() != null ? responseBody.getMessage()
                            : "SMS sent successfully",
                    responseBody != null ? responseBody.getRequestId() : null,
                    responseMap);
        } else {
            return SmsSendResult.failure(
                    responseBody != null && responseBody.getMessage() != null ? responseBody.getMessage()
                            : "Beem API Error",
                    responseBody != null && responseBody.getCode() != null ? String.valueOf(responseBody.getCode())
                            : String.valueOf(response.getStatusCode().value()),
                    responseMap);
        }
    }

    private Map<String, Object> extractErrorResponse(Exception e) {
        if (isHttpError(e)) {
            return parseHttpErrorResponse(e);
        }
        return Map.of("error", e.getMessage());
    }

    private boolean isHttpError(Exception e) {
        return e.getMessage().contains("400") || e.getMessage().contains("Bad Request");
    }

    private Map<String, Object> parseHttpErrorResponse(Exception e) {
        try {
            String jsonStr = extractJsonFromErrorMessage(e.getMessage());
            if (jsonStr != null) {
                return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception jsonEx) {
            // If parsing fails, fall back to simple error map
        }
        return Map.of("error", e.getMessage());
    }

    private String extractJsonFromErrorMessage(String errorMessage) {
        if (!errorMessage.contains("\"")) {
            return null;
        }

        int jsonStart = errorMessage.indexOf("{");
        int jsonEnd = errorMessage.lastIndexOf("}") + 1;

        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return errorMessage.substring(jsonStart, jsonEnd);
        }

        return null;
    }

    @Data
    static class BeemSmsRequest {
        @JsonProperty("source_addr")
        private String sourceAddr;

        @JsonProperty("schedule_time")
        private String scheduleTime;

        private String encoding;
        private String message;
        private List<Recipient> recipients;
    }

    @Data
    static class Recipient {
        @JsonProperty("dest_addr")
        private String destAddr;

        @JsonProperty("recipient_id")
        private String recipientId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class BeemSmsResponse {
        private Boolean successful;
        private String message;
        private Integer code;

        @JsonProperty("request_id")
        private String requestId;

        BeemSmsResponse(boolean successful, String message, int code) {
            this.successful = successful;
            this.message = message;
            this.code = code;
        }
    }

    private record BeemRequestSettings(String encoding, String scheduleTime) {
    }
}
