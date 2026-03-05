package com.flexcodelabs.flextuma.modules.webhook.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.modules.connector.services.ConnectorConfigService;
import com.flexcodelabs.flextuma.modules.connector.services.DataHydratorService;
import com.flexcodelabs.flextuma.modules.notification.services.NotificationService;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.webhooks.DlrParser;
import com.flexcodelabs.flextuma.core.webhooks.DlrResult;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SmsWebhookControllerTest {

    @Mock
    private SmsLogRepository logRepository;

    @Mock
    private DlrParser dlrParser;

    @Mock
    private ConnectorConfigService configService;

    @Mock
    private DataHydratorService hydratorService;

    @Mock
    private NotificationService notificationService;

    private SmsWebhookController buildController() {
        return new SmsWebhookController(logRepository, List.of(dlrParser), configService, hydratorService,
                notificationService);
    }

    private Map<String, Object> payload(String msgId, String status) {
        Map<String, Object> map = new HashMap<>();
        map.put("messageID", msgId);
        map.put("status", status);
        return map;
    }

    @Test
    void deliveryReport_shouldUpdateToSent_whenDelivered() {
        when(dlrParser.getProvider()).thenReturn("BEEM");
        when(dlrParser.parse(any())).thenReturn(new DlrResult("msg-123", SmsLogStatus.SENT, "delivered"));

        SmsLog log = new SmsLog();
        log.setStatus(SmsLogStatus.SENT);
        when(logRepository.findByProviderResponse("msg-123")).thenReturn(Optional.of(log));
        when(logRepository.save(any())).thenReturn(log);

        buildController().deliveryReport("beem", payload("msg-123", "delivered"));

        verify(logRepository).findByProviderResponse("msg-123");
        verify(logRepository).save(any());
        assertEquals(SmsLogStatus.SENT, log.getStatus());
    }

    @Test
    void deliveryReport_shouldUpdateToFailed_whenFailed() {
        when(dlrParser.getProvider()).thenReturn("BEEM");
        when(dlrParser.parse(any())).thenReturn(new DlrResult("msg-789", SmsLogStatus.FAILED, "failed"));

        SmsLog log = new SmsLog();
        log.setStatus(SmsLogStatus.PROCESSING);
        when(logRepository.findByProviderResponse("msg-789")).thenReturn(Optional.of(log));
        when(logRepository.save(any())).thenReturn(log);

        buildController().deliveryReport("beem", payload("msg-789", "failed"));

        verify(logRepository).save(any());
        assertEquals(SmsLogStatus.FAILED, log.getStatus());
    }

    @Test
    void deliveryReport_shouldNotSave_whenAlreadySentAndFailedDlrArrives() {
        when(dlrParser.getProvider()).thenReturn("BEEM");
        when(dlrParser.parse(any())).thenReturn(new DlrResult("msg-456", SmsLogStatus.FAILED, "failed"));

        SmsLog log = new SmsLog();
        log.setStatus(SmsLogStatus.SENT);
        lenient().when(logRepository.findByProviderResponse("msg-456")).thenReturn(Optional.of(log));

        buildController().deliveryReport("beem", payload("msg-456", "failed"));

        verify(logRepository, never()).save(any());
    }

    @Test
    void deliveryReport_shouldNotSave_whenUnknownProvider() {
        when(dlrParser.getProvider()).thenReturn("BEEM");

        buildController().deliveryReport("unknown_provider", payload("msg-000", "delivered"));

        verify(logRepository, never()).save(any());
    }

    @Test
    void deliveryReport_shouldNotSave_whenNoLogFound() {
        when(dlrParser.getProvider()).thenReturn("BEEM");
        when(dlrParser.parse(any())).thenReturn(new DlrResult("msg-999", SmsLogStatus.SENT, "delivered"));

        buildController().deliveryReport("beem", payload("msg-999", "delivered"));

        verify(logRepository, never()).save(any());
    }

    @Test
    void deliveryReport_shouldNotSave_whenIntermediateStatus() {
        when(dlrParser.getProvider()).thenReturn("BEEM");
        when(dlrParser.parse(any())).thenReturn(new DlrResult("msg-001", null, "submitted"));

        buildController().deliveryReport("beem", payload("msg-001", "submitted"));

        verify(logRepository, never()).save(any());
    }

    @Test
    void triggerDispatch_shouldCallHydratorAndQueueSms_systemUser() {
        UUID id = UUID.randomUUID();
        ConnectorConfig config = new ConnectorConfig();
        config.setId(id);
        config.setTenantId("tenant2");
        // No createdBy, so it should fallback to "system"

        when(configService.findById(id)).thenReturn(Optional.of(config));

        List<Map<String, String>> mockRecipients = List.of(
                new HashMap<>(Map.of("phoneNumber", "+254700000000")),
                new HashMap<>(Map.of("phoneNumber", "+254711111111")));
        when(hydratorService.getRecipients(eq("tenant2"), any())).thenReturn(mockRecipients);

        DispatchRequest req = new DispatchRequest();
        req.setTemplateCode("ALERT");
        req.setProvider("beem");
        req.setFilterQuery(Map.of("group", "ALL"));

        ResponseEntity<Map<String, Object>> res = buildController().triggerDispatch(id, req);

        assertEquals(200, res.getStatusCode().value());
        assertEquals(2, res.getBody().get("queued"));
        assertEquals(2, res.getBody().get("totalFetched"));

        verify(notificationService, times(2)).queueTemplatedSms(any(), eq("system"));
    }

    @Test
    void triggerDispatch_shouldCallHydratorAndQueueSms_withCreatedBy() {
        UUID id = UUID.randomUUID();
        ConnectorConfig config = new ConnectorConfig();
        config.setId(id);
        config.setTenantId("tenant3");
        User creator = new User();
        creator.setUsername("john_doe");
        config.setCreatedBy(creator);

        when(configService.findById(id)).thenReturn(Optional.of(config));

        List<Map<String, String>> mockRecipients = List.of(
                new HashMap<>(Map.of("phoneNumber", "+254999999999")));
        when(hydratorService.getRecipients(eq("tenant3"), any())).thenReturn(mockRecipients);

        DispatchRequest req = new DispatchRequest();
        req.setTemplateCode("ALERT");

        ResponseEntity<Map<String, Object>> res = buildController().triggerDispatch(id, req);

        assertEquals(200, res.getStatusCode().value());
        assertEquals(1, res.getBody().get("queued"));

        verify(notificationService).queueTemplatedSms(any(), eq("john_doe"));
    }
}
