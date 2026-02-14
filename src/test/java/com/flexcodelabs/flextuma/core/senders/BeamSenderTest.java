package com.flexcodelabs.flextuma.core.senders;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
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
class BeamSenderTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private BeamSender beamSender;

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
    void getProvider_shouldReturnBeam() {
        assertEquals("BEAM", beamSender.getProvider());
    }

    @Test
    void sendSms_shouldReturnSuccess_whenApiCallIsSuccessful() {
        BeamSender.BeamSmsResponse responseBody = new BeamSender.BeamSmsResponse(true, "SMS sent successfully", 100);
        ResponseEntity<BeamSender.BeamSmsResponse> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.postForEntity(eq(config.getUrl()), any(HttpEntity.class),
                eq(BeamSender.BeamSmsResponse.class)))
                .thenReturn(responseEntity);

        String result = beamSender.sendSms(config, "255712345678", "Hello World");

        assertEquals("SMS sent successfully", result);
    }

    @Test
    void sendSms_shouldReturnSuccess_whenResponseIsNull() {
        // If body is null, it defaults to "SUCCESS"
        ResponseEntity<BeamSender.BeamSmsResponse> responseEntity = new ResponseEntity<>(
                (BeamSender.BeamSmsResponse) null, HttpStatus.OK);

        when(restTemplate.postForEntity(eq(config.getUrl()), any(HttpEntity.class),
                eq(BeamSender.BeamSmsResponse.class)))
                .thenReturn(responseEntity);

        String result = beamSender.sendSms(config, "255712345678", "Hello World");

        assertEquals("SUCCESS", result);
    }

    @Test
    void sendSms_shouldThrowException_whenConnectionFails() {
        when(restTemplate.postForEntity(eq(config.getUrl()), any(HttpEntity.class),
                eq(BeamSender.BeamSmsResponse.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection failed"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> beamSender.sendSms(config, "255712345678", "Hello World"));
    }
}
