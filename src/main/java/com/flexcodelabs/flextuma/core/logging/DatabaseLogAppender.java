package com.flexcodelabs.flextuma.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.logging.SystemLog;
import com.flexcodelabs.flextuma.core.enums.LogLevel;

import javax.sql.DataSource;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DatabaseLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 2000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INSERT_SQL = "INSERT INTO system_log (id, timestamp, level, source, message, trace_id, stack_trace, metadata, username) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)";

    private static final AtomicReference<DataSource> dataSource = new AtomicReference<>();
    private static final CopyOnWriteArrayList<Consumer<SystemLog>> listeners = new CopyOnWriteArrayList<>();

    private final ConcurrentLinkedQueue<SystemLog> buffer = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService scheduler;

    private int batchSize = DEFAULT_BATCH_SIZE;
    private long flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS;

    public static void setDataSource(DataSource ds) {
        dataSource.set(ds);
    }

    public static void addListener(Consumer<SystemLog> listener) {
        listeners.add(listener);
    }

    public static void removeListener(Consumer<SystemLog> listener) {
        listeners.remove(listener);
    }

    // visible for testing
    public static void clearListeners() {
        listeners.clear();
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    @Override
    public void start() {
        super.start();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-log-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        flush();
        if (scheduler != null) {
            scheduler.shutdown();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLoggerName().startsWith("com.flexcodelabs.flextuma.core.logging")) {
            return;
        }

        SystemLog log = mapEvent(event);
        buffer.add(log);

        for (Consumer<SystemLog> listener : listeners) {
            try {
                listener.accept(log);
            } catch (Exception e) {
                listeners.remove(listener);
            }
        }

        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    private SystemLog mapEvent(ILoggingEvent event) {
        SystemLog log = new SystemLog();
        log.setId(UUID.randomUUID());
        log.setTimestamp(LocalDateTime.now());
        log.setLevel(mapLevel(event.getLevel()));
        log.setSource(extractSource(event.getLoggerName()));

        String msg = event.getFormattedMessage();
        if (msg != null) {
            msg = msg.replaceAll("\u001B\\[[;\\d]*m", "");
        }
        log.setMessage(msg);
        log.setTraceId(event.getMDCPropertyMap().get("traceId"));
        log.setUsername(event.getMDCPropertyMap().get("username"));

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            log.setStackTrace(ThrowableProxyUtil.asString(throwable));
        }

        return log;
    }

    private LogLevel mapLevel(Level level) {
        return switch (level.toInt()) {
            case Level.DEBUG_INT -> LogLevel.DEBUG;
            case Level.INFO_INT -> LogLevel.INFO;
            case Level.WARN_INT -> LogLevel.WARNING;
            case Level.ERROR_INT -> LogLevel.ERROR;
            default -> LogLevel.INFO;
        };
    }

    private String extractSource(String loggerName) {
        if (loggerName == null) {
            return "Unknown";
        }
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot >= 0 ? loggerName.substring(lastDot + 1) : loggerName;
    }

    @SuppressWarnings("java:S106") // System.err is intentional here — using a logger would cause infinite
                                   // recursion
    private void flush() {
        DataSource ds = dataSource.get();
        if (buffer.isEmpty() || ds == null) {
            return;
        }

        try (Connection conn = ds.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            conn.setAutoCommit(false);
            int count = 0;
            SystemLog log;

            while ((log = buffer.poll()) != null && count < batchSize * 2) {
                ps.setObject(1, log.getId());
                ps.setTimestamp(2, Timestamp.valueOf(log.getTimestamp()));
                ps.setString(3, log.getLevel().name());
                ps.setString(4, log.getSource());
                ps.setString(5, log.getMessage());
                ps.setString(6, log.getTraceId());
                ps.setString(7, log.getStackTrace());
                ps.setString(8, log.getMetadata() != null ? toJson(log.getMetadata()) : null);
                ps.setString(9, log.getUsername());
                ps.addBatch();
                count++;
            }

            if (count > 0) {
                ps.executeBatch();
                conn.commit();
            }
        } catch (Exception e) {
            PrintStream stderr = System.err;
            stderr.println("[DatabaseLogAppender] Failed to flush logs: " + e.getMessage());
        }
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
