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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

    @Test
    void sendSms_shouldUseDocumentedOptionalSettingsAndRequestId() {
        config.setExtraSettings("{\"encoding\":\"8\",\"schedule_time\":\"2026-08-15 10:30\"}");
        BeemSender.BeemSmsResponse responseBody = new BeemSender.BeemSmsResponse(true,
                "Message Submitted Successfully", 100);
        responseBody.setRequestId("67");
        ResponseEntity<BeemSender.BeemSmsResponse> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.postForEntity(eq(config.getUrl()), any(HttpEntity.class), eq(BeemSender.BeemSmsResponse.class)))
                .thenReturn(response);

        SmsSendResult result = beemSender.sendSms(config, "255712345678", "Hello World");

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(config.getUrl()), captor.capture(), eq(BeemSender.BeemSmsResponse.class));
        BeemSender.BeemSmsRequest request = (BeemSender.BeemSmsRequest) captor.getValue().getBody();
        assertEquals("8", request.getEncoding());
        assertEquals("2026-08-15 10:30", request.getScheduleTime());
        assertEquals("Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ=", captor.getValue().getHeaders().getFirst("Authorization"));
        assertEquals("67", result.getProviderMessageId());
        assertEquals("Message Submitted Successfully", result.getMessage());
    }
}
