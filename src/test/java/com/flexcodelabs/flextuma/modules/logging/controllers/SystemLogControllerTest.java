package com.flexcodelabs.flextuma.modules.logging.controllers;

import com.flexcodelabs.flextuma.core.entities.logging.SystemLog;
import com.flexcodelabs.flextuma.core.enums.LogLevel;
import com.flexcodelabs.flextuma.modules.logging.services.SystemLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemLogControllerTest {

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private SystemLogController controller;

    @Test
    void getAll_shouldReturnPaginatedResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        SystemLog log = new SystemLog();
        log.setLevel(LogLevel.ERROR);
        log.setMessage("Test");
        Page<SystemLog> page = new PageImpl<>(List.of(log), pageable, 1);

        when(systemLogService.findAll(pageable, null, null, null, null, null)).thenReturn(page);

        Map<String, Object> response = controller.getAll(pageable, null, null, null, null, null);

        assertEquals(0, response.get("page"));
        assertEquals(1L, response.get("total"));
        assertEquals(20, response.get("pageSize"));
        assertNotNull(response.get("systemLog"));
    }

    @Test
    void getAll_shouldPassFiltersToService() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        Page<SystemLog> page = new PageImpl<>(List.of());

        when(systemLogService.findAll(pageable, LogLevel.ERROR, "Notification", "tr_abc", from, to))
                .thenReturn(page);

        Map<String, Object> response = controller.getAll(pageable, LogLevel.ERROR, "Notification", "tr_abc", from, to);

        assertNotNull(response);
        verify(systemLogService).findAll(pageable, LogLevel.ERROR, "Notification", "tr_abc", from, to);
    }

    @Test
    void tail_shouldReturnSseEmitter() {
        SseEmitter emitter = new SseEmitter();
        when(systemLogService.streamLogs(LogLevel.ERROR)).thenReturn(emitter);

        SseEmitter result = controller.tail(LogLevel.ERROR);

        assertSame(emitter, result);
    }

    @Test
    void health_shouldReturnOkWithHealthData() {
        Map<String, Object> healthData = Map.of("status", "ONLINE", "uptimeMs", 1000L);
        when(systemLogService.getSystemHealth()).thenReturn(healthData);

        ResponseEntity<Map<String, Object>> result = controller.health();

        assertEquals(200, result.getStatusCode().value());
        assertEquals("ONLINE", result.getBody().get("status"));
    }

    @Test
    void purge_shouldReturnDeletedCount() {
        when(systemLogService.purgeOlderThan(30)).thenReturn(42);

        ResponseEntity<Map<String, Object>> result = controller.purge(30);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("42 log entries purged", result.getBody().get("message"));
        assertEquals(30, result.getBody().get("olderThanDays"));
    }

    @Test
    void purge_shouldUseDefaultDays() {
        when(systemLogService.purgeOlderThan(30)).thenReturn(0);

        ResponseEntity<Map<String, Object>> result = controller.purge(30);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("0 log entries purged", result.getBody().get("message"));
    }
}
