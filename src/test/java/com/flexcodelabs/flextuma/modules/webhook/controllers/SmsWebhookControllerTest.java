package com.flexcodelabs.flextuma.modules.webhook.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.webhooks.DlrParser;
import com.flexcodelabs.flextuma.core.webhooks.DlrResult;

@ExtendWith(MockitoExtension.class)
class SmsWebhookControllerTest {

    @Mock
    private SmsLogRepository logRepository;

    @Mock
    private DlrParser dlrParser;

    private SmsWebhookController buildController() {
        return new SmsWebhookController(logRepository, List.of(dlrParser));
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
}
