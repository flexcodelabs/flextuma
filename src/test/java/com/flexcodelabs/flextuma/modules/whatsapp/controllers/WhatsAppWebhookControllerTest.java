package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock
    private WhatsAppWebhookConfigRepository configRepository;

    @Mock
    private SmsLogRepository smsLogRepository;

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WhatsAppWebhookController controller() {
        return new WhatsAppWebhookController(configRepository, smsLogRepository, restTemplate, objectMapper);
    }

    private WhatsAppWebhookConfig activeConfig() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setPhoneNumberId("104725069208652");
        config.setCallbackUrl("https://example.com/hook");
        config.setVerifyToken("secret-verify-token");
        config.setCallbackToken("callback-token");
        config.setActive(true);
        return config;
    }

    @Test
    void verify_shouldMarkVerifiedAndSaveConfig_whenModeAndTokenMatch() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByVerifyTokenAndActiveTrue("secret-verify-token")).thenReturn(Optional.of(config));

        ResponseEntity<String> response = controller().verify("subscribe", "secret-verify-token", "the-challenge");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("the-challenge", response.getBody());
        assertNotNull(config.getLastVerifiedAt());
        verify(configRepository).save(config);
    }

    @Test
    void verify_shouldReturnForbidden_whenTokenDoesNotMatch() {
        when(configRepository.findByVerifyTokenAndActiveTrue("wrong-token")).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller().verify("subscribe", "wrong-token", "the-challenge");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void receive_shouldMarkEventReceived_whenSignatureIsValid() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"104725069208652\"}}}]}]}";

        ResponseEntity<Void> response = controller().receive(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(config.getLastEventAt());
        verify(configRepository).save(config);
        verify(restTemplate).postForEntity(any(String.class), any(), any());
    }

    @Test
    void receive_shouldNotMarkEventReceived_whenNoActiveConfigMatches() {
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.empty());

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"104725069208652\"}}}]}]}";

        ResponseEntity<Void> response = controller().receive(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(configRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void receiveGeneratedCallback_shouldAccept_whenPayloadHasNoPhoneNumberId() {
        // Meta delivers many event types (template status updates, account alerts, etc.) with no
        // metadata.phone_number_id at all. The callback token alone must be enough to accept these.
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByCallbackTokenAndActiveTrue("callback-token")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"event\":\"template_status_update\"}}]}]}";

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("callback-token", payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(config.getLastEventAt());
        verify(configRepository).save(config);
    }

    @Test
    void receiveGeneratedCallback_shouldAccept_whenPhoneNumberIdDiffersFromConfig() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByCallbackTokenAndActiveTrue("callback-token")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"some-other-number\"}}}]}]}";

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("callback-token", payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(config.getLastEventAt());
    }

    @Test
    void receiveGeneratedCallback_shouldReturnOkWithoutSaving_whenTokenUnknown() {
        when(configRepository.findByCallbackTokenAndActiveTrue("missing-token")).thenReturn(Optional.empty());

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"104725069208652\"}}}]}]}";

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("missing-token", payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(configRepository, org.mockito.Mockito.never()).save(any());
    }
}
