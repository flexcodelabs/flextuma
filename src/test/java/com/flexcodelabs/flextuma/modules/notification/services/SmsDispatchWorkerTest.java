package com.flexcodelabs.flextuma.modules.notification.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.services.SmsSender;

@ExtendWith(MockitoExtension.class)
class SmsDispatchWorkerTest {

    @Mock
    private SmsLogRepository logRepository;

    @Mock
    private SmsSender smsSender;

    private SmsDispatchWorker worker;

    @BeforeEach
    void setUp() {
        worker = new SmsDispatchWorker(logRepository, List.of(smsSender));
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
        when(smsSender.sendSms(any(), any(), any())).thenReturn("provider-msg-id-123");
        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        worker.dispatch();

        assertEquals(SmsLogStatus.SENT, log.getStatus());
        assertEquals("provider-msg-id-123", log.getProviderResponse());
    }

    @Test
    void dispatch_shouldRetry_whenSendFailsAndRetriesBelow3() {
        SmsLog log = pendingLog("beem");
        when(smsSender.getProvider()).thenReturn("beem");
        when(logRepository.findDueMessages(eq(SmsLogStatus.PENDING), any(java.time.LocalDateTime.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(log));
        when(smsSender.sendSms(any(), any(), any())).thenThrow(new RuntimeException("timeout"));
        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        worker.dispatch();

        assertEquals(SmsLogStatus.PENDING, log.getStatus());
        assertEquals(1, log.getRetries());
    }

    @Test
    void dispatch_shouldMarkFailed_whenMaxRetriesReached() {
        SmsLog log = pendingLog("beem");
        log.setRetries(2);

        when(smsSender.getProvider()).thenReturn("beem");
        when(logRepository.findDueMessages(eq(SmsLogStatus.PENDING), any(java.time.LocalDateTime.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(log));
        when(smsSender.sendSms(any(), any(), any())).thenThrow(new RuntimeException("timeout"));
        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        worker.dispatch();

        assertEquals(SmsLogStatus.FAILED, log.getStatus());
        assertEquals(3, log.getRetries());
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
