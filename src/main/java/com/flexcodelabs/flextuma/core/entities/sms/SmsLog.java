package com.flexcodelabs.flextuma.core.entities.sms;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "smslog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)

public class SmsLog extends Owner {

    public static final String PLURAL = "smsLogs";
    public static final String NAME_PLURAL = "SmsLogs";
    public static final String NAME_SINGULAR = "SmsLog";

    public static final String ALL = "ALL";
    public static final String READ = ALL;
    public static final String ADD = ALL;
    public static final String DELETE = ALL;
    public static final String UPDATE = ALL;

    private String recipient;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT", name = "status")
    @Enumerated(EnumType.STRING)
    private SmsLogStatus status;

    @Column(name = "retries", nullable = false)
    private int retries = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector", nullable = true)
    private SmsConnector connector;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> providerResponse;

    @Column(name = "provider_message_id", nullable = true)
    private String providerMessageId;

    @Column(columnDefinition = "TEXT", nullable = true)
    private String error;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template")
    private SmsTemplate template;

    @Column(name = "scheduled_at", nullable = true)
    private java.time.LocalDateTime scheduledAt;

}
