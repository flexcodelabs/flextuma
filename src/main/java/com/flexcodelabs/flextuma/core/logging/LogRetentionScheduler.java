package com.flexcodelabs.flextuma.core.logging;

import com.flexcodelabs.flextuma.core.repositories.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LogRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionScheduler.class);

    private final SystemLogRepository systemLogRepository;

    @Value("${flextuma.logging.retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 0 2 * * *")
    public void purgeOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = systemLogRepository.deleteByTimestampBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} system log entries older than {} days", deleted, retentionDays);
        }
    }
}
