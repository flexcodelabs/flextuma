package com.flexcodelabs.flextuma.core.senders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class BeamSender implements SmsSender {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProvider() {
        return "BEAM";
    }

    @Override
    public String sendSms(SmsConnector config, String to, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Set API keys in headers as per original logic
            headers.set("api_key", config.getKey());
            headers.set("secret_key", config.getSecret());

            String auth = config.getKey() + ":" + config.getSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            // Populate Request Body using new camelCase setters
            BeamSmsRequest requestBody = new BeamSmsRequest();
            requestBody.setSourceAddr(config.getSenderId());
            requestBody.setMessage(message);
            requestBody.setScheduleTime("");
            requestBody.setEncoding("0");

            Recipient recipient = new Recipient();
            recipient.setDestAddr(to);
            recipient.setRecipientId("1");
            requestBody.setRecipients(Collections.singletonList(recipient));

            HttpEntity<BeamSmsRequest> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<BeamSmsResponse> response = restTemplate.postForEntity(
                    config.getUrl(),
                    entity,
                    BeamSmsResponse.class);

            BeamSmsResponse responseBody = response.getBody();

            if (responseBody != null && !responseBody.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Beem API Error: " + responseBody.getMessage());
            }

            log.info("BEAM: SMS sent successfully to {}", to);
            return responseBody != null ? responseBody.getMessage() : "SUCCESS";

        } catch (Exception e) {
            log.error("BEAM Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to send via Beem: " + e.getMessage());
        }
    }

    @Data
    private static class BeamSmsRequest {
        @JsonProperty("source_addr")
        private String sourceAddr;

        @JsonProperty("schedule_time")
        private String scheduleTime;

        private String encoding;
        private String message;
        private List<Recipient> recipients;
    }

    @Data
    private static class Recipient {
        @JsonProperty("dest_addr")
        private String destAddr;

        @JsonProperty("recipient_id")
        private String recipientId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class BeamSmsResponse {
        private boolean valid;
        private String message;
        private int code;
    }
}