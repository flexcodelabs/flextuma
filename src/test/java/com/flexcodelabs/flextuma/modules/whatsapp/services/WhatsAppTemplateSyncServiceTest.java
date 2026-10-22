package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppTemplateSyncResultDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.services.meta.MetaGraphClient;
import com.flexcodelabs.flextuma.modules.whatsapp.services.templates.WhatsAppTemplateFetcher;
import com.flexcodelabs.flextuma.modules.whatsapp.services.templates.WhatsAppTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppTemplateSyncServiceTest {
    private static final String GRAPH = "https://graph.facebook.com/v21.0";
    private static final String WABA = "999888777";
    private static final String OTHER_WABA = "555444333";

    @Mock private WhatsAppTemplateRepository repository;
    @Mock private SmsConnectorRepository connectorRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private RestTemplate restTemplate;

    private WhatsAppTemplateSyncService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new WhatsAppTemplateSyncService(repository, connectorRepository, currentUserResolver,
                new WhatsAppTemplateFetcher(new MetaGraphClient(restTemplate, objectMapper)), new WhatsAppTemplateMapper(objectMapper));
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        lenient().when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(testUser));
        lenient().when(repository.save(any(WhatsAppTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SmsConnector connector(String senderId, String waba, String token) {
        SmsConnector connector = new SmsConnector();
        connector.setId(UUID.randomUUID());
        connector.setProvider("WHATSAPP");
        connector.setUrl(GRAPH);
        connector.setKey(token);
        connector.setSenderId(senderId);
        connector.setExtraSettings(waba == null ? null : "{\"businessAccountId\":\"" + waba + "\"}");
        connector.setCreatedBy(testUser);
        return connector;
    }

    private void givenConnectors(SmsConnector... connectors) {
        when(connectorRepository.findAllByCreatedByAndProviderAndActiveTrue(testUser, "WHATSAPP")).thenReturn(List.of(connectors));
    }

    private static Map<String, Object> template(String id, String name) {
        return Map.of("id", id, "name", name, "category", "UTILITY", "language", "en", "status", "APPROVED",
                "components", List.of(Map.of("type", "BODY", "text", "Feed is low, refill by {{1}}")));
    }

    private void metaReturns(String waba, Map<String, Object> body) {
        when(restTemplate.exchange(eq(GRAPH + "/" + waba + "/message_templates?limit=100"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    private static HttpClientErrorException expiredToken() {
        byte[] body = "{\"error\":{\"message\":\"Error validating access token\",\"code\":190}}".getBytes(StandardCharsets.UTF_8);
        return HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8);
    }

    private List<WhatsAppTemplate> savedTemplates(int times) {
        ArgumentCaptor<WhatsAppTemplate> saved = ArgumentCaptor.forClass(WhatsAppTemplate.class);
        verify(repository, times(times)).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void syncForCurrentUser_shouldThrowWhenNoConnector() {
        givenConnectors();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.syncForCurrentUser(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void sync_shouldReportNumbersWithoutAWabaId_withoutCallingMeta() {
        givenConnectors(connector("111", null, "t"));

        WhatsAppTemplateSyncResultDTO result = service.syncForCurrentUser(null);

        assertEquals(1, result.accounts().size());
        assertNull(result.accounts().get(0).businessAccountId());
        assertTrue(result.accounts().get(0).error().contains("WhatsApp Business Account ID"));
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void sync_shouldUpsertNewTemplatesAndMarkMissingOnesRemoved() {
        SmsConnector number = connector("111", WABA, "t");
        givenConnectors(number);
        metaReturns(WABA, Map.of("data", List.of(template("META_ID_NEW", "farm_alert"))));
        WhatsAppTemplate stale = new WhatsAppTemplate();
        stale.setMetaTemplateId("META_ID_STALE");
        stale.setStatus("APPROVED");
        stale.setBusinessAccountId(WABA);
        stale.setConnector(number);
        when(repository.findByCreatedBy(testUser)).thenReturn(new ArrayList<>(List.of(stale)));

        WhatsAppTemplateSyncResultDTO result = service.syncForCurrentUser(null);

        assertEquals(1, result.synced());
        assertEquals(1, result.removed());
        List<WhatsAppTemplate> saved = savedTemplates(2);
        WhatsAppTemplate created = saved.stream().filter(t -> "META_ID_NEW".equals(t.getMetaTemplateId())).findFirst().orElseThrow();
        assertEquals(WABA, created.getBusinessAccountId());
        assertTrue(created.getPlaceholdersJson().contains("\"position\":1"));
        assertEquals(WhatsAppTemplate.STATUS_REMOVED, stale.getStatus());
    }

    @Test
    void sync_shouldFetchEachAccountOnce_andReuseRowsSyncedThroughAnotherNumber() {
        SmsConnector first = connector("111", WABA, "t");
        SmsConnector second = connector("222", WABA, "t");
        givenConnectors(first, second);
        metaReturns(WABA, Map.of("data", List.of(template("META_1", "farm_alert"))));
        WhatsAppTemplate syncedViaSecond = new WhatsAppTemplate();
        syncedViaSecond.setMetaTemplateId("META_1");
        syncedViaSecond.setConnector(second);
        when(repository.findByCreatedBy(testUser)).thenReturn(List.of(syncedViaSecond));

        WhatsAppTemplateSyncResultDTO result = service.syncForCurrentUser(null);

        assertEquals(1, result.accounts().size());
        assertEquals(List.of("111", "222"), result.accounts().get(0).phoneNumberIds());
        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
        assertSame(syncedViaSecond, savedTemplates(1).get(0));
        assertEquals(WABA, syncedViaSecond.getBusinessAccountId());
    }

    @Test
    void sync_shouldFallBackToAnotherNumbersToken_whenOneIsRevoked() {
        givenConnectors(connector("111", WABA, "revoked"), connector("222", WABA, "good"));
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), argThat((HttpEntity<?> e) -> e != null && "Bearer revoked".equals(e.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))), eq(Map.class)))
                .thenThrow(expiredToken());
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), argThat((HttpEntity<?> e) -> e != null && "Bearer good".equals(e.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(template("META_1", "farm_alert")))));

        WhatsAppTemplateSyncResultDTO result = service.syncForCurrentUser(null);

        assertNull(result.accounts().get(0).error());
        assertEquals(1, result.synced());
    }

    @Test
    void sync_shouldKeepSyncingOtherAccounts_whenOneFails() {
        givenConnectors(connector("111", WABA, "revoked"), connector("333", OTHER_WABA, "good"));
        when(restTemplate.exchange(eq(GRAPH + "/" + WABA + "/message_templates?limit=100"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(expiredToken());
        metaReturns(OTHER_WABA, Map.of("data", List.of(template("META_2", "otp"))));

        WhatsAppTemplateSyncResultDTO result = service.syncForCurrentUser(null);

        assertTrue(result.accounts().get(0).error().contains("invalid or has expired"));
        assertEquals(1, result.accounts().get(1).synced());
    }

    @Test
    void sync_shouldOnlySyncTheRequestedAccount() {
        givenConnectors(connector("111", WABA, "t"), connector("333", OTHER_WABA, "t"));
        metaReturns(OTHER_WABA, Map.of("data", List.of()));

        WhatsAppTemplateSyncResultDTO result = service.syncForCurrentUser(OTHER_WABA);

        assertEquals(List.of(OTHER_WABA), result.accounts().stream().map(WhatsAppTemplateSyncResultDTO.Account::businessAccountId).toList());
        assertThrows(ResponseStatusException.class, () -> service.syncForCurrentUser("000"));
    }

    @Test
    void sync_shouldFollowCursorPagination() {
        givenConnectors(connector("111", WABA, "t"));
        metaReturns(WABA, Map.of("data", List.of(template("META_1", "a")),
                "paging", Map.of("next", "https://graph.facebook.com/next", "cursors", Map.of("after", "CURSOR2"))));
        when(restTemplate.exchange(eq(GRAPH + "/" + WABA + "/message_templates?limit=100&after=CURSOR2"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(template("META_2", "b")))));

        assertEquals(2, service.syncForCurrentUser(null).synced());
    }
}
