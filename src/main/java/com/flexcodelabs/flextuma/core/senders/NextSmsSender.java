package com.flexcodelabs.flextuma.core.senders;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import com.flexcodelabs.flextuma.core.services.SmsSender;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NextSmsSender implements SmsSender {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NextSmsSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProvider() {
        return "NEXT";
    }

    @Override
    public SmsSendResult sendSms(SmsConnector config, String to, String message) {
        try {
            HttpEntity<NextSmsRequest> entity = buildRequestEntity(config, to, message);
            ResponseEntity<NextSmsResponse> response = restTemplate.postForEntity(
                    config.getUrl(), entity, NextSmsResponse.class);

            return processResponse(response);
        } catch (Exception e) {
            log.error("NextSMS Error: {}", e.getMessage());
            return handleException(e);
        }
    }

    private HttpEntity<NextSmsRequest> buildRequestEntity(SmsConnector config, String to, String message) {
        HttpHeaders headers = createHeaders(config);
        NextSmsRequest requestBody = createRequestBody(config, to, message);
        return new HttpEntity<>(requestBody, headers);
    }

    private HttpHeaders createHeaders(SmsConnector config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        String auth = config.getKey() + ":" + config.getSecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);

        return headers;
    }

    private NextSmsRequest createRequestBody(SmsConnector config, String to, String message) {
        NextSmsRequest requestBody = new NextSmsRequest();
        requestBody.setFrom(config.getSenderId());
        requestBody.setTo(to);
        requestBody.setText(message);
        return requestBody;
    }

    private SmsSendResult processResponse(ResponseEntity<NextSmsResponse> response) {
        NextSmsResponse responseBody = response.getBody();
        Map<String, Object> responseMap = objectMapper.convertValue(responseBody,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

        if (hasMessages(responseBody)) {
            return handleMessageDetail(responseBody, responseMap);
        }

        return SmsSendResult.success("SMS sent successfully", null, responseMap);
    }

    private boolean hasMessages(NextSmsResponse responseBody) {
        return responseBody != null && responseBody.getMessages() != null && !responseBody.getMessages().isEmpty();
    }

    private SmsSendResult handleMessageDetail(NextSmsResponse responseBody, Map<String, Object> responseMap) {
        NextSmsResponse.MessageDetail detail = responseBody.getMessages().get(0);
        if (isErrorStatus(detail)) {
            log.warn("NextSMS: Potential error in delivery: {}", detail.getStatus().getName());
            return SmsSendResult.failure(
                    "Potential delivery error: " + detail.getStatus().getName(),
                    String.valueOf(detail.getStatus().getGroupId()),
                    responseMap);
        }
        return SmsSendResult.success(
                "SMS sent successfully",
                detail.getMessageId(),
                responseMap);
    }

    private boolean isErrorStatus(NextSmsResponse.MessageDetail detail) {
        return detail.getStatus() != null && detail.getStatus().getGroupId() > 2;
    }

    private SmsSendResult handleException(Exception e) {
        Map<String, Object> errorResponse = parseErrorResponse(e);
        return SmsSendResult.failure(
                "Failed to send via NextSMS: " + e.getMessage(),
                "SEND_ERROR",
                errorResponse);
    }

    private Map<String, Object> parseErrorResponse(Exception e) {
        if (e.getMessage().contains("\"")) {
            return extractJsonFromErrorMessage(e.getMessage());
        }
        return Map.of("error", e.getMessage());
    }

    private Map<String, Object> extractJsonFromErrorMessage(String errorMessage) {
        try {
            int jsonStart = errorMessage.indexOf("{");
            int jsonEnd = errorMessage.lastIndexOf("}") + 1;
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                String jsonStr = errorMessage.substring(jsonStart, jsonEnd);
                return objectMapper.readValue(jsonStr,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            }
        } catch (Exception jsonEx) {
            // Fall through to default error response
        }
        return Map.of("error", errorMessage);
    }

    @lombok.Data
    static class NextSmsRequest {
        private String from;
        private String to;
        private String text;
    }

    @lombok.Data
    static class NextSmsResponse {
        private List<MessageDetail> messages;

        @lombok.Data
        static class MessageDetail {
            private String to;
            private Status detail;
            private Status status;
            private String messageId;
        }

        @lombok.Data
        static class Status {
            private int groupId;
            private String groupName;
            private int id;
            private String name;
            private String description;
        }
    }
}