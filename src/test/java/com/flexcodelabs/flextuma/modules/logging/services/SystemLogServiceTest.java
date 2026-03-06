package com.flexcodelabs.flextuma.modules.logging.services;

import com.flexcodelabs.flextuma.core.entities.logging.SystemLog;
import com.flexcodelabs.flextuma.core.enums.LogLevel;
import com.flexcodelabs.flextuma.core.repositories.SystemLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemLogServiceTest {

    @Mock
    private SystemLogRepository repository;

    @InjectMocks
    private SystemLogService service;

    @SuppressWarnings("unchecked")
    @Test
    void findAll_shouldReturnPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 20);
        SystemLog log = new SystemLog();
        log.setLevel(LogLevel.ERROR);
        log.setSource("TestService");
        log.setMessage("Test error");
        Page<SystemLog> expected = new PageImpl<>(List.of(log));

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<SystemLog> result = service.findAll(pageable, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_shouldApplyLevelFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<SystemLog> expected = new PageImpl<>(List.of());

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<SystemLog> result = service.findAll(pageable, LogLevel.ERROR, null, null, null, null);

        assertNotNull(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_shouldApplyAllFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<SystemLog> expected = new PageImpl<>(List.of());
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<SystemLog> result = service.findAll(pageable, LogLevel.WARNING, "Notification", "tr_abc123", from, to);

        assertNotNull(result);
    }

    @Test
    void purgeOlderThan_shouldDeleteOldLogs() {
        when(repository.deleteByTimestampBefore(any(LocalDateTime.class))).thenReturn(42);

        int deleted = service.purgeOlderThan(30);

        assertEquals(42, deleted);
        verify(repository).deleteByTimestampBefore(any(LocalDateTime.class));
    }

    @Test
    void streamLogs_shouldReturnSseEmitter() {
        SseEmitter emitter = service.streamLogs(null);

        assertNotNull(emitter);
    }

    @Test
    void streamLogs_shouldReturnSseEmitterWithLevelFilter() {
        SseEmitter emitter = service.streamLogs(LogLevel.ERROR);

        assertNotNull(emitter);
    }

    @Test
    void getSystemHealth_shouldReturnHealthInfo() {
        Map<String, Object> health = service.getSystemHealth();

        assertNotNull(health);
        assertEquals("ONLINE", health.get("status"));
        assertNotNull(health.get("uptime"));
        assertNotNull(health.get("uptimeMs"));
        assertNotNull(health.get("memory"));
        assertNotNull(health.get("activeThreads"));
        assertNotNull(health.get("availableProcessors"));
        assertNotNull(health.get("retentionDays"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getSystemHealth_shouldContainMemoryInfo() {
        Map<String, Object> health = service.getSystemHealth();

        Map<String, Object> memory = (Map<String, Object>) health.get("memory");
        assertNotNull(memory);
        assertNotNull(memory.get("totalMb"));
        assertNotNull(memory.get("freeMb"));
        assertNotNull(memory.get("usedMb"));
        assertNotNull(memory.get("maxMb"));
    }
}
