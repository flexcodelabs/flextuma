package com.flexcodelabs.flextuma.core.entities.logging;

import com.flexcodelabs.flextuma.core.enums.LogLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "system_log", indexes = {
        @Index(name = "idx_syslog_timestamp", columnList = "timestamp"),
        @Index(name = "idx_syslog_level", columnList = "level"),
        @Index(name = "idx_syslog_source", columnList = "source"),
        @Index(name = "idx_syslog_trace_id", columnList = "trace_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LogLevel level;

    @Column(nullable = false, updatable = false)
    private String source;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String message;

    @Column(name = "trace_id", updatable = false)
    private String traceId;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String stackTrace;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> metadata;

    @Column(updatable = false)
    private String username;

    @PrePersist
    void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
