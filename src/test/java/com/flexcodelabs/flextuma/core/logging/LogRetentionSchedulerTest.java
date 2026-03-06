package com.flexcodelabs.flextuma.core.logging;

import com.flexcodelabs.flextuma.core.repositories.SystemLogRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogRetentionSchedulerTest {

    @Mock
    private SystemLogRepository systemLogRepository;

    @InjectMocks
    private LogRetentionScheduler scheduler;

    @ParameterizedTest(name = "retentionDays={0}, deletedCount={1}")
    @CsvSource({
            "30, 10",
            "30, 0",
            "7, 5"
    })
    void purgeOldLogs_shouldDeleteByConfiguredRetention(int retentionDays, int deletedCount) {
        ReflectionTestUtils.setField(scheduler, "retentionDays", retentionDays);
        when(systemLogRepository.deleteByTimestampBefore(any(LocalDateTime.class))).thenReturn(deletedCount);

        scheduler.purgeOldLogs();

        verify(systemLogRepository).deleteByTimestampBefore(any(LocalDateTime.class));
    }
}
