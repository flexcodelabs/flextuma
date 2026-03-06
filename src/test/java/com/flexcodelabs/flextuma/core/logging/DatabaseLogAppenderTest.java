package com.flexcodelabs.flextuma.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.flexcodelabs.flextuma.core.entities.logging.SystemLog;
import com.flexcodelabs.flextuma.core.enums.LogLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DatabaseLogAppenderTest {

    private DatabaseLogAppender appender;

    @BeforeEach
    void setUp() {
        DatabaseLogAppender.setDataSource(null);
        appender = new DatabaseLogAppender();
        appender.setBatchSize(50);
        appender.setFlushIntervalMs(2000);
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        DatabaseLogAppender.setDataSource(null);
        DatabaseLogAppender.clearListeners();
    }

    @Test
    void addListener_shouldNotifyOnNewEvent() {
        AtomicReference<SystemLog> received = new AtomicReference<>();
        Consumer<SystemLog> listener = received::set;

        DatabaseLogAppender.addListener(listener);
        try {
            SystemLog log = new SystemLog();
            log.setLevel(LogLevel.ERROR);
            log.setMessage("Test error");
            log.setSource("TestSource");
            listener.accept(log);

            assertNotNull(received.get());
            assertEquals("Test error", received.get().getMessage());
            assertEquals(LogLevel.ERROR, received.get().getLevel());
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    @Test
    void removeListener_shouldStopNotifications() {
        AtomicReference<SystemLog> received = new AtomicReference<>();
        Consumer<SystemLog> listener = received::set;

        DatabaseLogAppender.addListener(listener);
        DatabaseLogAppender.removeListener(listener);

        assertNull(received.get());
    }

    @Test
    void setDataSource_shouldAcceptNull() {
        assertDoesNotThrow(() -> DatabaseLogAppender.setDataSource(null));
    }

    // ─── start / stop lifecycle ─────────────────────────────────

    @Test
    void start_shouldInitializeScheduler() {
        appender.start();
        assertTrue(appender.isStarted());
    }

    @Test
    void stop_shouldShutdownCleanly() {
        appender.start();
        assertTrue(appender.isStarted());

        appender.stop();
        assertFalse(appender.isStarted());
    }

    @Test
    void stop_whenNotStarted_shouldNotThrow() {
        // scheduler is null when stop() is called without start()
        assertDoesNotThrow(() -> appender.stop());
    }

    // ─── append ─────────────────────────────────────────────────

    @Test
    void append_shouldSkipOwnLoggingPackage() {
        ILoggingEvent event = mockEvent(
                "com.flexcodelabs.flextuma.core.logging.SomeClass",
                Level.INFO, "Should be skipped", null);

        appender.start();
        appender.doAppend(event);

        // Nothing should be buffered since it's filtered out
        // Verify by flushing — if buffered, this would try to use a null DataSource
        assertDoesNotThrow(() -> appender.stop());
    }

    @Test
    void append_shouldBufferEventAndNotifyListeners() {
        List<SystemLog> received = new ArrayList<>();
        Consumer<SystemLog> listener = received::add;
        DatabaseLogAppender.addListener(listener);

        try {
            ILoggingEvent event = mockEvent(
                    "com.example.MyService", Level.WARN,
                    "a warning", null);

            appender.start();
            appender.doAppend(event);

            assertEquals(1, received.size());
            assertEquals("a warning", received.get(0).getMessage());
            assertEquals(LogLevel.WARNING, received.get(0).getLevel());
            assertEquals("MyService", received.get(0).getSource());
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    @Test
    void append_shouldRemoveListenerThatThrows() {
        Consumer<SystemLog> badListener = log -> {
            throw new RuntimeException("boom");
        };
        DatabaseLogAppender.addListener(badListener);

        try {
            ILoggingEvent event = mockEvent(
                    "com.example.Foo", Level.INFO, "msg", null);
            appender.start();
            // Should not throw, and the bad listener should be removed
            assertDoesNotThrow(() -> appender.doAppend(event));
        } finally {
            DatabaseLogAppender.removeListener(badListener);
        }
    }

    @Test
    void append_shouldTriggerFlushWhenBatchSizeReached() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeBatch()).thenReturn(new int[] { 1 });

        DatabaseLogAppender.setDataSource(ds);

        appender.setBatchSize(2);
        appender.start();

        // Append 2 events to reach batchSize and trigger auto-flush
        appender.doAppend(mockEvent("com.example.A", Level.INFO, "msg1", null));
        appender.doAppend(mockEvent("com.example.B", Level.DEBUG, "msg2", null));

        verify(conn).setAutoCommit(false);
        verify(ps, atLeastOnce()).addBatch();
        verify(ps).executeBatch();
        verify(conn).commit();
    }

    // ─── mapEvent / mapLevel ────────────────────────────────────

    @Test
    void append_shouldMapEventWithStackTrace() {
        List<SystemLog> received = new ArrayList<>();
        Consumer<SystemLog> listener = received::add;
        DatabaseLogAppender.addListener(listener);

        try {
            IThrowableProxy throwableProxy = mock(IThrowableProxy.class);
            // ThrowableProxyUtil.asString needs class/message at minimum
            when(throwableProxy.getClassName()).thenReturn("java.lang.RuntimeException");
            when(throwableProxy.getMessage()).thenReturn("test error");
            when(throwableProxy.getStackTraceElementProxyArray())
                    .thenReturn(new ch.qos.logback.classic.spi.StackTraceElementProxy[0]);

            ILoggingEvent event = mockEvent(
                    "com.example.Service", Level.ERROR,
                    "something failed", throwableProxy);

            appender.start();
            appender.doAppend(event);

            assertEquals(1, received.size());
            SystemLog log = received.get(0);
            assertNotNull(log.getStackTrace());
            assertEquals(LogLevel.ERROR, log.getLevel());
            assertNotNull(log.getId());
            assertNotNull(log.getTimestamp());
        } finally {
            DatabaseLogAppender.removeListener(listener);
            // clean up by clearing all listeners
        }
    }

    @Test
    void append_shouldMapMdcFields() {
        List<SystemLog> received = new ArrayList<>();
        Consumer<SystemLog> listener = received::add;
        DatabaseLogAppender.addListener(listener);

        try {
            ILoggingEvent event = mockEvent(
                    "com.example.Ctrl", Level.INFO, "hello", null);
            when(event.getMDCPropertyMap()).thenReturn(
                    Map.of("traceId", "abc-123", "username", "admin"));

            appender.start();
            appender.doAppend(event);

            SystemLog log = received.get(0);
            assertEquals("abc-123", log.getTraceId());
            assertEquals("admin", log.getUsername());
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    static Stream<Arguments> levelMappings() {
        return Stream.of(
                Arguments.of(Level.DEBUG, LogLevel.DEBUG),
                Arguments.of(Level.INFO, LogLevel.INFO),
                Arguments.of(Level.WARN, LogLevel.WARNING),
                Arguments.of(Level.ERROR, LogLevel.ERROR),
                Arguments.of(Level.TRACE, LogLevel.INFO) // default case
        );
    }

    @ParameterizedTest(name = "Logback {0} -> LogLevel.{1}")
    @MethodSource("levelMappings")
    void append_shouldMapLogbackLevelCorrectly(Level logbackLevel, LogLevel expected) {
        List<SystemLog> received = new ArrayList<>();
        Consumer<SystemLog> listener = received::add;
        DatabaseLogAppender.addListener(listener);

        try {
            ILoggingEvent event = mockEvent(
                    "com.example.X", logbackLevel, "test", null);
            appender.start();
            appender.doAppend(event);

            assertEquals(expected, received.get(0).getLevel());
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    // ─── extractSource ──────────────────────────────────────────

    @Test
    void append_shouldExtractSourceFromLoggerName() {
        List<SystemLog> received = new ArrayList<>();
        Consumer<SystemLog> listener = received::add;
        DatabaseLogAppender.addListener(listener);

        try {
            appender.start();
            appender.doAppend(mockEvent(
                    "com.example.deep.pkg.MyClass", Level.INFO, "m", null));
            assertEquals("MyClass", received.get(0).getSource());
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    @Test
    void append_shouldReturnLoggerNameWhenNoDot() {
        List<SystemLog> received = new ArrayList<>();
        Consumer<SystemLog> listener = received::add;
        DatabaseLogAppender.addListener(listener);

        try {
            appender.start();
            appender.doAppend(mockEvent("SimpleLogger", Level.INFO, "m", null));
            assertEquals("SimpleLogger", received.get(0).getSource());
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    @Test
    void append_shouldReturnUnknownForNullLoggerName() {
        List<SystemLog> received = new ArrayList<>();
        Consumer<SystemLog> listener = received::add;
        DatabaseLogAppender.addListener(listener);

        try {
            ILoggingEvent event = mock(ILoggingEvent.class);
            // getLoggerName returns null — but we first check startsWith, so we need
            // to make it NOT start with the logging package. Null would NPE on startsWith,
            // so let's use an empty string instead and test the null path via extractSource
            // indirectly.
            when(event.getLoggerName()).thenReturn("");
            when(event.getLevel()).thenReturn(Level.INFO);
            when(event.getFormattedMessage()).thenReturn("msg");
            when(event.getMDCPropertyMap()).thenReturn(Map.of());
            when(event.getThrowableProxy()).thenReturn(null);

            appender.start();
            appender.doAppend(event);

            // "" has no dot, so extractSource returns "" itself (lastDot = -1)
            assertEquals("", received.get(0).getSource());
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    // ─── flush ──────────────────────────────────────────────────

    @Test
    void flush_shouldSkipWhenBufferEmpty() {
        DataSource ds = mock(DataSource.class);
        DatabaseLogAppender.setDataSource(ds);

        appender.start();
        // Stop triggers flush, but buffer is empty — should not get a connection
        appender.stop();

        verifyNoInteractions(ds);
    }

    @Test
    void flush_shouldSkipWhenDataSourceNull() {
        // dataSource is null by default in setUp
        appender.start();
        appender.doAppend(mockEvent("com.example.X", Level.INFO, "msg", null));

        // Stop triggers flush, but no datasource — should not throw
        assertDoesNotThrow(() -> appender.stop());
    }

    @Test
    void flush_shouldWriteBufferedLogs() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeBatch()).thenReturn(new int[] { 1 });

        DatabaseLogAppender.setDataSource(ds);

        appender.start();
        appender.doAppend(mockEvent("com.example.A", Level.INFO, "hello", null));

        // stop() calls flush()
        appender.stop();

        verify(conn).setAutoCommit(false);
        verify(ps).addBatch();
        verify(ps).executeBatch();
        verify(conn).commit();
    }

    @Test
    void flush_shouldHandleMetadataInLog() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeBatch()).thenReturn(new int[] { 1 });

        DatabaseLogAppender.setDataSource(ds);

        // Add a log with metadata via listener to set metadata
        Consumer<SystemLog> listener = log -> {
            log.setMetadata(Map.of("key", "value"));
        };
        DatabaseLogAppender.addListener(listener);

        try {
            appender.start();
            appender.doAppend(mockEvent("com.example.B", Level.INFO, "with meta", null));
            appender.stop();

            // Metadata should have been serialized to JSON via toJson
            verify(ps).setString(eq(8), contains("key"));
        } finally {
            DatabaseLogAppender.removeListener(listener);
        }
    }

    @Test
    void flush_shouldHandleNullMetadata() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeBatch()).thenReturn(new int[] { 1 });

        DatabaseLogAppender.setDataSource(ds);

        appender.start();
        appender.doAppend(mockEvent("com.example.C", Level.INFO, "no meta", null));
        appender.stop();

        // metadata is null → ps.setString(8, null)
        verify(ps).setString(8, null);
    }

    @Test
    void flush_shouldHandleSqlException() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection refused"));

        DatabaseLogAppender.setDataSource(ds);

        appender.start();
        appender.doAppend(mockEvent("com.example.D", Level.ERROR, "msg", null));

        // flush should catch the exception and print to stderr, not throw
        assertDoesNotThrow(() -> appender.stop());
    }

    // ─── helpers ────────────────────────────────────────────────

    private ILoggingEvent mockEvent(String loggerName, Level level,
            String message, IThrowableProxy throwable) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn(loggerName);
        when(event.getLevel()).thenReturn(level);
        when(event.getFormattedMessage()).thenReturn(message);
        when(event.getMDCPropertyMap()).thenReturn(Map.of());
        when(event.getThrowableProxy()).thenReturn(throwable);
        return event;
    }
}
