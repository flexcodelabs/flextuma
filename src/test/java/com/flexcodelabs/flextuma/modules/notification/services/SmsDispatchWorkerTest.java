package com.flexcodelabs.flextuma.modules.notification.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.senders.WhatsAppSender;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import com.flexcodelabs.flextuma.core.services.SmsSender;

@ExtendWith(MockitoExtension.class)
class SmsDispatchWorkerTest {

    @Mock
    private SmsLogRepository logRepository;

    @Mock
    private SmsSender smsSender;

    @Mock
    private WhatsAppSender whatsAppSender;

    @Mock
    private NotificationService notificationService;

    private SmsDispatchWorker worker;

    @BeforeEach
    void setUp() {
        MessageRetryPolicy retryPolicy = new MessageRetryPolicy(10_000, 2.0, 3_600_000, 43_200_000);
        worker = new SmsDispatchWorker(logRepository, List.of(smsSender), whatsAppSender, notificationService,
                retryPolicy);
    }

    private void stubDue(SmsLog log) {
        when(logRepository.findDueMessages(eq(SmsLogStatus.PENDING), any(LocalDateTime.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(log));
        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(logRepository.claimPendingMessage(any(), eq(SmsLogStatus.PENDING), eq(SmsLogStatus.PROCESSING)))
                .thenReturn(1);
    }

    private static SmsSendResult transientFailure() {
        return SmsSendResult.failure("503 Service Unavailable", "SEND_ERROR", Map.of());
    }

    private SmsLog pendingLog(String provider) {
        SmsConnector connector = new SmsConnector();
        connector.setProvider(provider);

        SmsLog log = new SmsLog();
        log.setStatus(SmsLogStatus.PENDING);
        log.setRecipient("255700000000");
        log.setContent("Test message");
        log.setConnector(connector);
        log.setRetries(0);
        return log;
    }

    @Test
    void dispatch_shouldMarkSent_whenSendSucceeds() {
        SmsLog log = pendingLog("beem");
        when(smsSender.getProvider()).thenReturn("beem");
        when(logRepository.findDueMessages(eq(SmsLogStatus.PENDING), any(java.time.LocalDateTime.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(log));
        when(smsSender.sendSms(any(), any(), any())).thenReturn(
                SmsSendResult.success("SMS sent successfully", "provider-msg-id-123",
                        Map.of("status", "sent", "message_id", "provider-msg-id-123")));
        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));
		when(logRepository.claimPendingMessage(any(), eq(SmsLogStatus.PENDING), eq(SmsLogStatus.PROCESSING))).thenReturn(1);

        worker.dispatch();

        assertEquals(SmsLogStatus.SENT, log.getStatus());
        assertEquals("provider-msg-id-123", log.getProviderMessageId());
        assertEquals(Map.of("status", "sent", "message_id", "provider-msg-id-123"), log.getProviderResponse());
    }

    @Test
    void dispatch_shouldScheduleBackoffRetry_whenFailureIsTransient() {
        SmsLog log = pendingLog("beem");
        when(smsSender.getProvider()).thenReturn("beem");
        when(smsSender.sendSms(any(), any(), any())).thenReturn(transientFailure());
        stubDue(log);

        LocalDateTime before = LocalDateTime.now();
        worker.dispatch();

        assertEquals(SmsLogStatus.PENDING, log.getStatus());
        assertEquals(1, log.getRetries());
        assertEquals(1, log.getDeliveryAttempts());
        assertEquals("503 Service Unavailable", log.getError());
        assertFalse(log.getNextRetryAt().isBefore(before.plusSeconds(10)));
    }

    @Test
    void dispatch_shouldBackOffExponentially_onLaterAttempts() {
        SmsLog log = pendingLog("beem");
        log.setDeliveryAttempts(1);
        log.setRetries(1);
        when(smsSender.getProvider()).thenReturn("beem");
        when(smsSender.sendSms(any(), any(), any())).thenReturn(transientFailure());
        stubDue(log);

        LocalDateTime before = LocalDateTime.now();
        worker.dispatch();

        assertEquals(SmsLogStatus.PENDING, log.getStatus());
        assertEquals(2, log.getRetries());
        assertFalse(log.getNextRetryAt().isBefore(before.plusSeconds(20)));
        assertTrue(log.getNextRetryAt().isBefore(before.plusSeconds(40)));
    }

    @Test
    void dispatch_shouldKeepRetrying_whileInsideRetryWindow() {
        SmsLog log = pendingLog("beem");
        log.setDeliveryAttempts(20);
        log.setRetries(20);
        log.setRetryWindowStartedAt(LocalDateTime.now().minusHours(11));
        when(smsSender.getProvider()).thenReturn("beem");
        when(smsSender.sendSms(any(), any(), any())).thenReturn(transientFailure());
        stubDue(log);

        LocalDateTime before = LocalDateTime.now();
        worker.dispatch();

        assertEquals(SmsLogStatus.PENDING, log.getStatus());
        assertEquals(21, log.getRetries());
        // Backoff is capped at max-interval (1h).
        assertFalse(log.getNextRetryAt().isBefore(before.plusHours(1)));
        assertTrue(log.getNextRetryAt().isBefore(before.plusHours(1).plusSeconds(5)));
    }

    @Test
    void dispatch_shouldMarkFailed_whenRetryWindowExhausted() {
        SmsLog log = pendingLog("beem");
        log.setDeliveryAttempts(20);
        log.setRetries(20);
        log.setRetryWindowStartedAt(LocalDateTime.now().minusHours(12).minusMinutes(1));
        when(smsSender.getProvider()).thenReturn("beem");
        when(smsSender.sendSms(any(), any(), any())).thenReturn(transientFailure());
        stubDue(log);

        worker.dispatch();

        assertEquals(SmsLogStatus.FAILED, log.getStatus());
        assertEquals(21, log.getDeliveryAttempts());
        assertEquals(20, log.getRetries());
        assertNull(log.getNextRetryAt());
    }

    @Test
    void dispatch_shouldRetry_whenProviderRejectsWith4xx() {
        SmsLog log = pendingLog("beem");
        when(smsSender.getProvider()).thenReturn("beem");
        when(smsSender.sendSms(any(), any(), any()))
                .thenReturn(SmsSendResult.failure("400 Bad Request", "SEND_ERROR", Map.of()));
        stubDue(log);

        worker.dispatch();

        assertEquals(SmsLogStatus.PENDING, log.getStatus());
        assertEquals(1, log.getRetries());
        assertNotNull(log.getRetryWindowStartedAt());
        assertNotNull(log.getNextRetryAt());
    }

    @Test
    void dispatch_shouldRetry_whenSenderThrows() {
        SmsLog log = pendingLog("beem");
        when(smsSender.getProvider()).thenReturn("beem");
        when(smsSender.sendSms(any(), any(), any())).thenThrow(new RuntimeException("timeout"));
        stubDue(log);

        worker.dispatch();

        assertEquals(SmsLogStatus.PENDING, log.getStatus());
        assertEquals(1, log.getRetries());
        assertEquals("timeout", log.getError());
    }

    @Test
    void dispatch_shouldResendWhatsAppTemplate_andBillIt() {
        SmsLog log = pendingLog("WHATSAPP");
        log.setContent("[Template: farm_alert/en] Feed is low");
        log.setWhatsappTemplate(Map.of("name", "farm_alert", "language", "en", "components", List.of()));
        when(whatsAppSender.sendTemplate(any(), eq("255700000000"), eq("farm_alert"), eq("en"), any()))
                .thenReturn(SmsSendResult.success("accepted", "wamid.1", Map.of()));
        stubDue(log);

        worker.dispatch();

        assertEquals(SmsLogStatus.SENT, log.getStatus());
        assertEquals("wamid.1", log.getProviderMessageId());
        verify(smsSender, never()).sendSms(any(), any(), any());
        verify(notificationService).chargeTemplateSend(any(), eq(log.getConnector()),
                eq("[Template: farm_alert/en] Feed is low"), eq("255700000000"));
    }

    @Test
    void dispatch_shouldMarkFailed_whenNoConnector() {
        SmsLog log = new SmsLog();
        log.setStatus(SmsLogStatus.PENDING);
        log.setConnector(null);

        when(logRepository.findDueMessages(eq(SmsLogStatus.PENDING), any(java.time.LocalDateTime.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(log));
        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));
		when(logRepository.claimPendingMessage(any(), eq(SmsLogStatus.PENDING), eq(SmsLogStatus.PROCESSING))).thenReturn(1);

        worker.dispatch();

        assertEquals(SmsLogStatus.FAILED, log.getStatus());
    }

    @Test
    void dispatch_shouldDoNothing_whenNoPendingLogs() {
        when(logRepository.findDueMessages(eq(SmsLogStatus.PENDING), any(java.time.LocalDateTime.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Collections.emptyList());

        worker.dispatch();

        verify(logRepository, never()).save(any());
    }
}
