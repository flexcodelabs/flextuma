package com.flexcodelabs.flextuma.core.entities.logging;

import com.flexcodelabs.flextuma.core.enums.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SystemLogTest {

    @Test
    void testNoArgsConstructor() {
        SystemLog log = new SystemLog();
        assertNull(log.getId());
        assertNull(log.getTimestamp());
        assertNull(log.getLevel());
        assertNull(log.getSource());
        assertNull(log.getMessage());
        assertNull(log.getTraceId());
        assertNull(log.getStackTrace());
        assertNull(log.getMetadata());
        assertNull(log.getUsername());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LogLevel level = LogLevel.ERROR;
        String source = "Source";
        String message = "Message";
        String traceId = "trace-123";
        String stackTrace = "stack trace";
        Map<String, Object> metadata = Map.of("key", "value");
        String username = "user";

        SystemLog log = new SystemLog(id, now, level, source, message, traceId, stackTrace, metadata, username);

        assertEquals(id, log.getId());
        assertEquals(now, log.getTimestamp());
        assertEquals(level, log.getLevel());
        assertEquals(source, log.getSource());
        assertEquals(message, log.getMessage());
        assertEquals(traceId, log.getTraceId());
        assertEquals(stackTrace, log.getStackTrace());
        assertEquals(metadata, log.getMetadata());
        assertEquals(username, log.getUsername());
    }

    @Test
    void testGettersAndSetters() {
        SystemLog log = new SystemLog();
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LogLevel level = LogLevel.INFO;
        String source = "App";
        String message = "Hello";
        String traceId = "abc";
        String stackTrace = "error at...";
        Map<String, Object> metadata = Map.of("foo", "bar");
        String username = "admin";

        log.setId(id);
        log.setTimestamp(now);
        log.setLevel(level);
        log.setSource(source);
        log.setMessage(message);
        log.setTraceId(traceId);
        log.setStackTrace(stackTrace);
        log.setMetadata(metadata);
        log.setUsername(username);

        assertEquals(id, log.getId());
        assertEquals(now, log.getTimestamp());
        assertEquals(level, log.getLevel());
        assertEquals(source, log.getSource());
        assertEquals(message, log.getMessage());
        assertEquals(traceId, log.getTraceId());
        assertEquals(stackTrace, log.getStackTrace());
        assertEquals(metadata, log.getMetadata());
        assertEquals(username, log.getUsername());
    }

    @Test
    void testPrePersist() {
        SystemLog log = new SystemLog();
        assertNull(log.getTimestamp());

        log.prePersist();
        assertNotNull(log.getTimestamp());
    }

    @Test
    void testPrePersistWithExistingTimestamp() {
        SystemLog log = new SystemLog();
        LocalDateTime existing = LocalDateTime.of(2023, 1, 1, 10, 0);
        log.setTimestamp(existing);

        log.prePersist();
        assertEquals(existing, log.getTimestamp());
    }
}
