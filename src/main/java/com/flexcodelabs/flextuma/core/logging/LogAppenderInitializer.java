package com.flexcodelabs.flextuma.core.logging;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class LogAppenderInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void init() {
        DatabaseLogAppender.setDataSource(dataSource);
    }
}
