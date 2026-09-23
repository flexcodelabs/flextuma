package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppTemplateSyncServiceTest {

    @Mock
    private WhatsAppTemplateRepository repository;

    @Mock
    private SmsConnectorRepository connectorRepository;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WhatsAppTemplateSyncService service;

    private User testUser;
    private SmsConnector connector;

    @BeforeEach
    void setUp() {
        service = new WhatsAppTemplateSyncService(repository, connectorRepository, currentUserResolver, restTemplate,
                objectMapper);

        testUser = new User();
        testUser.setId(UUID.randomUUID());

        connector = new SmsConnector();
        connector.setId(UUID.randomUUID());
        connector.setProvider("WHATSAPP");
        connector.setUrl("https://graph.facebook.com/v21.0");
        connector.setKey("test-token");
        connector.setExtraSettings("{\"businessAccountId\":\"999888777\"}");
        connector.setCreatedBy(testUser);
    }

    private Map<String, Object> metaResponse(List<Map<String, Object>> templates) {
        return Map.of("data", templates, "paging", Map.of());
    }

    @Test
    void syncForCurrentUser_shouldThrowWhenNoConnector() {
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(connectorRepository.findAllByCreatedByAndProviderAndActiveTrue(testUser, "WHATSAPP"))
                .thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, service::syncForCurrentUser);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void syncForCurrentUser_shouldThrowWhenConnectorHasNoBusinessAccountId() {
        connector.setExtraSettings(null);
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(testUser));
        when(connectorRepository.findAllByCreatedByAndProviderAndActiveTrue(testUser, "WHATSAPP"))
                .thenReturn(List.of(connector));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, service::syncForCurrentUser);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("businessAccountId"));
    }

    @Test
    void sync_shouldUpsertNewTemplateAndMarkMissingOneRemoved() {
        Map<String, Object> newTemplate = Map.of(
                "id", "META_ID_NEW",
                "name", "farm_alert",
                "category", "UTILITY",
                "language", "en",
                "status", "APPROVED",
                "components", List.of(Map.of("type", "BODY", "text", "Feed is low, refill by {{1}}")));

        when(restTemplate.exchange(eq("https://graph.facebook.com/v21.0/999888777/message_templates?limit=100"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(metaResponse(List.of(newTemplate)), HttpStatus.OK));

        WhatsAppTemplate staleTemplate = new WhatsAppTemplate();
        staleTemplate.setMetaTemplateId("META_ID_STALE");
        staleTemplate.setStatus("APPROVED");
        staleTemplate.setConnector(connector);
        staleTemplate.setCreatedBy(testUser);

        when(repository.findByConnectorAndCreatedBy(connector, testUser)).thenReturn(List.of(staleTemplate));
        when(repository.save(any(WhatsAppTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<WhatsAppTemplate> upserted = service.sync(connector);

        assertEquals(1, upserted.size());
        assertEquals("farm_alert", upserted.get(0).getName());
        assertEquals("APPROVED", upserted.get(0).getStatus());
        assertTrue(upserted.get(0).getPlaceholdersJson().contains("\"position\":1"));

        ArgumentCaptor<WhatsAppTemplate> savedCaptor = ArgumentCaptor.forClass(WhatsAppTemplate.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).save(savedCaptor.capture());
        WhatsAppTemplate removed = savedCaptor.getAllValues().stream()
                .filter(t -> "META_ID_STALE".equals(t.getMetaTemplateId())).findFirst().orElseThrow();
        assertEquals(WhatsAppTemplate.STATUS_REMOVED, removed.getStatus());
    }
}
