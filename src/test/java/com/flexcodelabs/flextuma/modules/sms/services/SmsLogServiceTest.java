package com.flexcodelabs.flextuma.modules.sms.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsLogServiceTest {

    @Mock
    private SmsLogRepository smsLogRepository;

    @InjectMocks
    private SmsLogService smsLogService;

    private UUID logId;
    private SmsLog smsLog;

    @BeforeEach
    void setUp() {
        logId = UUID.randomUUID();
        smsLog = new SmsLog();
        smsLog.setId(logId);
        smsLog.setStatus(SmsLogStatus.FAILED);
        smsLog.setRetries(1);
        smsLog.setError("Network timeout");
        smsLog.setProviderResponse("HTTP 504");
    }

    @Test
    void retryFailedMessage_Success() {
        try (MockedStatic<com.flexcodelabs.flextuma.core.security.SecurityUtils> utils = mockStatic(
                com.flexcodelabs.flextuma.core.security.SecurityUtils.class)) {
            utils.when(com.flexcodelabs.flextuma.core.security.SecurityUtils::getCurrentUserAuthorities)
                    .thenReturn(Set.of("SUPER_ADMIN"));

            when(smsLogRepository.findById(logId)).thenReturn(Optional.of(smsLog));
            when(smsLogRepository.save(any(SmsLog.class))).thenAnswer(i -> i.getArgument(0));

            SmsLog retriedLog = smsLogService.retryFailedMessage(logId);

            assertNotNull(retriedLog);
            assertEquals(SmsLogStatus.PENDING, retriedLog.getStatus());
            assertEquals(2, retriedLog.getRetries());
            assertNull(retriedLog.getError());
            assertNull(retriedLog.getProviderResponse());

            verify(smsLogRepository).save(smsLog);
        }
    }

    @Test
    void retryFailedMessage_LogNotFound() {
        try (MockedStatic<com.flexcodelabs.flextuma.core.security.SecurityUtils> utils = mockStatic(
                com.flexcodelabs.flextuma.core.security.SecurityUtils.class)) {
            utils.when(com.flexcodelabs.flextuma.core.security.SecurityUtils::getCurrentUserAuthorities)
                    .thenReturn(Set.of("UPDATE_SMS_LOGS"));

            when(smsLogRepository.findById(logId)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> smsLogService.retryFailedMessage(logId));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    @Test
    void retryFailedMessage_NotFailedStatus() {
        try (MockedStatic<com.flexcodelabs.flextuma.core.security.SecurityUtils> utils = mockStatic(
                com.flexcodelabs.flextuma.core.security.SecurityUtils.class)) {
            utils.when(com.flexcodelabs.flextuma.core.security.SecurityUtils::getCurrentUserAuthorities)
                    .thenReturn(Set.of(SmsLog.UPDATE));

            smsLog.setStatus(SmsLogStatus.DELIVERED);
            when(smsLogRepository.findById(logId)).thenReturn(Optional.of(smsLog));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> smsLogService.retryFailedMessage(logId));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Only failed messages"));
        }
    }

    @Test
    void retryFailedMessage_NoPermission() {
        try (MockedStatic<com.flexcodelabs.flextuma.core.security.SecurityUtils> utils = mockStatic(
                com.flexcodelabs.flextuma.core.security.SecurityUtils.class)) {
            utils.when(com.flexcodelabs.flextuma.core.security.SecurityUtils::getCurrentUserAuthorities)
                    .thenReturn(Set.of("READ_SMS_LOGS"));

            assertThrows(AccessDeniedException.class, () -> smsLogService.retryFailedMessage(logId));
        }
    }
}
