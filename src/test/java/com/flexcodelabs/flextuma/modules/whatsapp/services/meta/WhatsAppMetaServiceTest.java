package com.flexcodelabs.flextuma.modules.whatsapp.services.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppMetaCheckRequest;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppMetaCheckResult;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppMetaServiceTest {
    private static final String GRAPH = "https://graph.facebook.com/v21.0";
    private static final String WABA = "102290129340398";
    private static final String PHONE = "104725069208652";

    @Mock private RestTemplate restTemplate;
    @Mock private SmsConnectorRepository connectorRepository;
    @Mock private CurrentUserResolver currentUserResolver;

    private WhatsAppMetaService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new WhatsAppMetaService(new MetaGraphClient(restTemplate, objectMapper), connectorRepository, currentUserResolver, objectMapper);
    }

    private void stubGet(String path, Map<String, Object> body) {
        when(restTemplate.exchange(startsWith(GRAPH + path), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    private static Map<String, Object> phone(String id, String display) {
        return Map.of("id", id, "display_phone_number", display, "verified_name", "Flextuma Support", "quality_rating", "GREEN");
    }

    private static WhatsAppMetaCheckRequest typed(String waba, String phoneId) {
        return new WhatsAppMetaCheckRequest(GRAPH, "EAAG-token", waba, phoneId, null);
    }

    @Test
    void check_shouldListWabaNumbersAndSubscribedApps() {
        stubGet("/" + WABA + "/phone_numbers", Map.of("data", List.of(phone(PHONE, "+255 700 000 001"), phone("999", "+255 700 000 002"))));
        stubGet("/" + WABA + "/subscribed_apps", Map.of("data", List.of(Map.of("whatsapp_business_api_data", Map.of("name", "Flextuma", "id", "1")))));

        WhatsAppMetaCheckResult result = service.check(typed(WABA, PHONE));

        assertTrue(result.ok());
        assertEquals(2, result.phoneNumbers().size());
        assertEquals("+255 700 000 001", result.phoneNumber().displayPhoneNumber());
        assertEquals("Flextuma Support", result.phoneNumber().verifiedName());
        assertEquals(List.of("Flextuma"), result.subscribedApps());
    }

    @Test
    void check_shouldSendTheTokenAsBearerAuth() {
        stubGet("/" + PHONE, phone(PHONE, "+255 700 000 001"));

        service.check(typed(null, PHONE));

        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(startsWith(GRAPH + "/" + PHONE), eq(HttpMethod.GET), entity.capture(), eq(Map.class));
        assertEquals("Bearer EAAG-token", entity.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void check_shouldFail_whenPhoneNumberIsNotInTheWaba() {
        stubGet("/" + WABA + "/phone_numbers", Map.of("data", List.of(phone("999", "+255 700 000 002"))));

        WhatsAppMetaCheckResult result = service.check(typed(WABA, PHONE));

        assertFalse(result.ok());
        assertEquals("NOT_IN_WABA", result.errorCode());
    }

    @Test
    void check_shouldExplainAnExpiredToken() {
        byte[] body = "{\"error\":{\"message\":\"Error validating access token\",\"code\":190}}".getBytes(StandardCharsets.UTF_8);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8));

        WhatsAppMetaCheckResult result = service.check(typed(null, PHONE));

        assertFalse(result.ok());
        assertEquals("190", result.errorCode());
        assertTrue(result.error().contains("invalid or has expired"));
    }

    @Test
    void check_shouldRefuseNonGraphHosts_withoutMakingARequest() {
        WhatsAppMetaCheckResult result = service.check(new WhatsAppMetaCheckRequest("https://internal.example.com", "EAAG", null, PHONE, null));

        assertEquals("INVALID_URL", result.errorCode());
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void check_shouldRejectNonNumericIds_soTheyCannotAlterTheRequestPath() {
        WhatsAppMetaCheckResult result = service.check(typed(null, "123/../me"));

        assertEquals("INVALID_ID", result.errorCode());
    }

    @Test
    void check_shouldRequireAToken() {
        assertEquals("MISSING_TOKEN", service.check(new WhatsAppMetaCheckRequest(GRAPH, " ", null, PHONE, null)).errorCode());
    }

    @Test
    void check_shouldNotUseAnotherUsersConnector() {
        SmsConnector connector = new SmsConnector();
        connector.setProvider("WHATSAPP");
        User owner = new User();
        owner.setId(UUID.randomUUID());
        connector.setCreatedBy(owner);
        User someoneElse = new User();
        someoneElse.setId(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        when(connectorRepository.findById(id)).thenReturn(Optional.of(connector));
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(someoneElse));

        WhatsAppMetaCheckResult result = service.check(new WhatsAppMetaCheckRequest(null, null, null, null, id));

        assertEquals("NOT_FOUND", result.errorCode());
    }

    @Test
    void check_shouldUseASavedConnectorsTokenAndIds() {
        SmsConnector connector = new SmsConnector();
        connector.setProvider("WHATSAPP");
        connector.setUrl(GRAPH);
        connector.setKey("saved-token");
        connector.setSenderId(PHONE);
        User owner = new User();
        owner.setId(UUID.randomUUID());
        connector.setCreatedBy(owner);
        UUID id = UUID.randomUUID();
        when(connectorRepository.findById(id)).thenReturn(Optional.of(connector));
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(owner));
        stubGet("/" + PHONE, phone(PHONE, "+255 700 000 001"));

        WhatsAppMetaCheckResult result = service.check(new WhatsAppMetaCheckRequest(null, null, null, null, id));

        assertTrue(result.ok());
        assertEquals(PHONE, result.phoneNumber().id());
    }

    @Test
    void subscribe_shouldPostSubscribedApps_thenReturnTheRefreshedCheck() {
        when(restTemplate.exchange(eq(GRAPH + "/" + WABA + "/subscribed_apps"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("success", true)));
        stubGet("/" + WABA + "/phone_numbers", Map.of("data", List.of(phone(PHONE, "+255 700 000 001"))));
        stubGet("/" + WABA + "/subscribed_apps", Map.of("data", List.of(Map.of("whatsapp_business_api_data", Map.of("name", "Flextuma")))));

        WhatsAppMetaCheckResult result = service.subscribe(typed(WABA, null));

        assertTrue(result.ok());
        assertEquals(List.of("Flextuma"), result.subscribedApps());
    }

    @Test
    void check_withTokenOnly_shouldListAnotherWabaWithoutDefaultingToTheConnectorsNumber() {
        SmsConnector connector = new SmsConnector();
        connector.setProvider("WHATSAPP");
        connector.setUrl(GRAPH);
        connector.setKey("saved-token");
        connector.setSenderId(PHONE);
        connector.setExtraSettings("{\"businessAccountId\":\"" + WABA + "\"}");
        User owner = new User();
        owner.setId(UUID.randomUUID());
        connector.setCreatedBy(owner);
        UUID id = UUID.randomUUID();
        when(connectorRepository.findById(id)).thenReturn(Optional.of(connector));
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(owner));
        stubGet("/402290129340222/phone_numbers", Map.of("data", List.of(phone("404725069208999", "+254 700 000 999"))));
        stubGet("/402290129340222/subscribed_apps", Map.of("data", List.of()));

        WhatsAppMetaCheckResult result = service.check(new WhatsAppMetaCheckRequest(null, null, "402290129340222", null, id, true));

        assertTrue(result.ok());
        assertEquals(1, result.phoneNumbers().size());
        assertEquals(null, result.phoneNumber());
    }
}
