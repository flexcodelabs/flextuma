package com.flexcodelabs.flextuma.core.senders;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Collections;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSender;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NextSmsSender implements SmsSender {

    private final RestTemplate restTemplate;

    public NextSmsSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProvider() {
        return "NEXT";
    }

    @Override
    public String sendSms(SmsConnector config, String to, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            String auth = config.getKey() + ":" + config.getSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            NextSmsRequest requestBody = new NextSmsRequest();
            requestBody.setFrom(config.getSenderId());
            requestBody.setTo(to);
            requestBody.setText(message);

            HttpEntity<NextSmsRequest> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<NextSmsResponse> response = restTemplate.postForEntity(
                    config.getUrl(),
                    entity,
                    NextSmsResponse.class);

            NextSmsResponse responseBody = response.getBody();

            if (responseBody != null && responseBody.getMessages() != null && !responseBody.getMessages().isEmpty()) {
                NextSmsResponse.MessageDetail detail = responseBody.getMessages().get(0);
                if (detail.getStatus() != null && detail.getStatus().getGroupId() > 2) {
                    log.warn("NextSMS: Potential error in delivery: {}", detail.getStatus().getName());
                }
                return detail.getMessageId();
            }

            return "SUCCESS";

        } catch (Exception e) {
            log.error("NextSMS Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to send via NextSMS: " + e.getMessage());
        }
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