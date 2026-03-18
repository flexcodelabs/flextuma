package com.flexcodelabs.flextuma.core.senders;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeemSenderTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private BeemSender beemSender;

    private SmsConnector config;

    @BeforeEach
    void setUp() {
        config = new SmsConnector();
        config.setUrl("http://api.beem.africa/v1/send");
        config.setKey("test-key");
        config.setSecret("test-secret");
        config.setSenderId("TEST_SENDER");
    }

    @Test
    void getProvider_shouldReturnBeem() {
        assertEquals("BEEM", beemSender.getProvider());
    }

    @Test
    void sendSms_shouldReturnSuccess_whenApiCallIsSuccessful() {
        BeemSender.BeemSmsResponse responseBody = new BeemSender.BeemSmsResponse(true, "SMS sent successfully", 100);
        ResponseEntity<BeemSender.BeemSmsResponse> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.postForEntity(eq(config.getUrl()), any(HttpEntity.class),
                eq(BeemSender.BeemSmsResponse.class)))
                .thenReturn(responseEntity);

        SmsSendResult result = beemSender.sendSms(config, "255712345678", "Hello World");

        assertEquals("SMS sent successfully", result.getMessage());
        assertTrue(result.isSuccess());
    }

    @Test
    void sendSms_shouldReturnSuccess_whenResponseIsNull() {
        // If body is null, it defaults to "SUCCESS"
        ResponseEntity<BeemSender.BeemSmsResponse> responseEntity = new ResponseEntity<>(
                (BeemSender.BeemSmsResponse) null, HttpStatus.OK);

        when(restTemplate.postForEntity(eq(config.getUrl()), any(HttpEntity.class),
                eq(BeemSender.BeemSmsResponse.class)))
                .thenReturn(responseEntity);

        SmsSendResult result = beemSender.sendSms(config, "255712345678", "Hello World");

        assertEquals("SMS sent successfully", result.getMessage());
        assertTrue(result.isSuccess());
    }

    @Test
    void sendSms_shouldReturnFailure_whenConnectionFails() {
        when(restTemplate.postForEntity(eq(config.getUrl()), any(HttpEntity.class),
                eq(BeemSender.BeemSmsResponse.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection failed"));

        SmsSendResult result = beemSender.sendSms(config, "255712345678", "Hello World");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Failed to send via Beem"));
        assertEquals("SEND_ERROR", result.getErrorCode());
        assertNotNull(result.getProviderResponse());
    }
}
