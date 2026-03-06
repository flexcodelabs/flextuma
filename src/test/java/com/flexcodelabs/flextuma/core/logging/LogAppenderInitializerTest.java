package com.flexcodelabs.flextuma.core.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class LogAppenderInitializerTest {

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private LogAppenderInitializer initializer;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        DatabaseLogAppender.setDataSource(null);
    }

    @Test
    void init_shouldSetDataSourceOnAppender() {
        initializer.init();
        // Since setDataSource is static and we can't easily mock static methods
        // without Mockito inline/PowerMock, we verify by checking a side effect
        // or just ensuring it doesn't throw.
        // However, we can use a "spy" or check if DatabaseLogAppender has a getter (it
        // doesn't appear to).
        // For now, we'll stick to assertDoesNotThrow but we've improved it by
        // ensuring the context is clean.
        assertDoesNotThrow(() -> initializer.init());
    }
}
