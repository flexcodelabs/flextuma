package com.flexcodelabs.flextuma.core.senders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppSenderTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WhatsAppSender sender;
    private SmsConnector config;

    @BeforeEach
    void setUp() {
        sender = new WhatsAppSender(restTemplate, objectMapper);
        config = new SmsConnector();
        config.setUrl("https://graph.facebook.com/v21.0");
        config.setKey("test-token");
        config.setSenderId("104725069208652");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedBody() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://graph.facebook.com/v21.0/104725069208652/messages"),
                captor.capture(), eq(Map.class));
        return captor.getValue().getBody();
    }

    private void stubSuccessResponse() {
        Map<String, Object> response = Map.of("messages", List.of(Map.of("id", "wamid.HBg")));
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    @Test
    void sendTemplate_shouldBuildTextBodyPayloadShape() {
        stubSuccessResponse();
        List<Map<String, Object>> components = List.of(
                Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", "Feed is running low"))));

        SmsSendResult result = sender.sendTemplate(config, "255712345678", "farm_alert", "en", components);

        assertTrue(result.isSuccess());
        assertEquals("wamid.HBg", result.getProviderMessageId());

        Map<String, Object> body = capturedBody();
        assertEquals("whatsapp", body.get("messaging_product"));
        assertEquals("255712345678", body.get("to"));
        assertEquals("template", body.get("type"));

        Map<String, Object> template = (Map<String, Object>) body.get("template");
        assertEquals("farm_alert", template.get("name"));
        assertEquals(Map.of("code", "en"), template.get("language"));
        assertEquals(components, template.get("components"));
    }

    @Test
    void sendTemplate_shouldPassThroughMediaHeaderComponent() {
        stubSuccessResponse();
        List<Map<String, Object>> components = List.of(
                Map.of("type", "header", "parameters",
                        List.of(Map.of("type", "image", "image", Map.of("link", "https://example.com/a.jpg")))));

        sender.sendTemplate(config, "255712345678", "farm_alert", "en", components);

        Map<String, Object> template = (Map<String, Object>) capturedBody().get("template");
        assertEquals(components, template.get("components"));
    }

    @Test
    void sendTemplate_shouldPassThroughDynamicUrlButtonComponent() {
        stubSuccessResponse();
        List<Map<String, Object>> components = List.of(
                Map.of("type", "button", "sub_type", "url", "index", "0",
                        "parameters", List.of(Map.of("type", "text", "text", "abc123"))));

        sender.sendTemplate(config, "255712345678", "farm_alert", "en", components);

        Map<String, Object> template = (Map<String, Object>) capturedBody().get("template");
        assertEquals(components, template.get("components"));
    }

    @Test
    void sendTemplate_shouldOmitComponentsKeyWhenNoneSupplied() {
        stubSuccessResponse();

        sender.sendTemplate(config, "255712345678", "otp_code", "en", List.of());

        Map<String, Object> template = (Map<String, Object>) capturedBody().get("template");
        assertFalse(template.containsKey("components"));
    }

    @Test
    void sendTemplate_shouldReturnFailure_whenConnectionFails() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection failed"));

        SmsSendResult result = sender.sendTemplate(config, "255712345678", "farm_alert", "en", List.of());

        assertFalse(result.isSuccess());
        assertEquals("SEND_ERROR", result.getErrorCode());
    }
}
