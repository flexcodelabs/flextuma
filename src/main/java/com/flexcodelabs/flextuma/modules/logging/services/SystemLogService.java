package com.flexcodelabs.flextuma.modules.logging.services;

import com.flexcodelabs.flextuma.core.entities.logging.SystemLog;
import com.flexcodelabs.flextuma.core.enums.LogLevel;
import com.flexcodelabs.flextuma.core.logging.DatabaseLogAppender;
import com.flexcodelabs.flextuma.core.repositories.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogRepository repository;

    @Value("${flextuma.logging.retention-days:30}")
    private int retentionDays;

    public Page<SystemLog> findAll(Pageable pageable, LogLevel level, String source, String traceId,
            LocalDateTime from, LocalDateTime to) {
        Specification<SystemLog> spec = (root, query, cb) -> cb.conjunction();

        if (level != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("level"), level));
        }
        if (source != null && !source.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("source")),
                    "%" + source.toLowerCase() + "%"));
        }
        if (traceId != null && !traceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("traceId"), traceId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to));
        }

        return repository.findAll(spec, pageable);
    }

    public SseEmitter streamLogs(LogLevel minLevel) {
        SseEmitter emitter = new SseEmitter(0L);

        Consumer<SystemLog> listener = log -> {
            if (minLevel != null && log.getLevel().ordinal() < minLevel.ordinal()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(log));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        };

        DatabaseLogAppender.addListener(listener);

        emitter.onCompletion(() -> DatabaseLogAppender.removeListener(listener));
        emitter.onTimeout(() -> DatabaseLogAppender.removeListener(listener));
        emitter.onError(e -> DatabaseLogAppender.removeListener(listener));

        return emitter;
    }

    @Transactional
    public int purgeOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return repository.deleteByTimestampBefore(cutoff);
    }

    public Map<String, Object> getSystemHealth() {
        Runtime runtime = Runtime.getRuntime();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "ONLINE");
        health.put("uptime", String.format("%dd %dh %dm %ds",
                uptime.toDays(), uptime.toHoursPart(), uptime.toMinutesPart(), uptime.toSecondsPart()));
        health.put("uptimeMs", uptimeMs);
        health.put("memory", Map.of(
                "totalMb", runtime.totalMemory() / (1024 * 1024),
                "freeMb", runtime.freeMemory() / (1024 * 1024),
                "usedMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                "maxMb", runtime.maxMemory() / (1024 * 1024)));
        health.put("activeThreads", Thread.activeCount());
        health.put("availableProcessors", runtime.availableProcessors());
        health.put("version", getClass().getPackage().getImplementationVersion());
        health.put("retentionDays", retentionDays);

        return health;
    }
}
