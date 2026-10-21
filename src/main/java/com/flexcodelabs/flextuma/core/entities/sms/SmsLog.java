package com.flexcodelabs.flextuma.core.entities.sms;

import java.util.Map;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    public static final String READ = "READ_SMS_LOGS";
    public static final String ADD = "ADD_SMS_LOGS";
    public static final String DELETE = "DELETE_SMS_LOGS";
    public static final String UPDATE = "UPDATE_SMS_LOGS";

    private String recipient;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT", name = "status")
    @Enumerated(EnumType.STRING)
    private SmsLogStatus status;

    /** Total re-sends of this message, automatic backoff retries and manual resends alike. */
    @Column(name = "retries", nullable = false)
    private int retries = 0;

    /** Send attempts in the current delivery cycle; a manual resend starts a fresh cycle. */
    @ColumnDefault("0")
    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts = 0;

    /** When the next automatic retry is due; null when none is scheduled. */
    @Column(name = "next_retry_at", nullable = true)
    private java.time.LocalDateTime nextRetryAt;

    /** First failure of the current delivery cycle; retries stop once the retry window from here runs out. */
    @Column(name = "retry_window_started_at", nullable = true)
    private java.time.LocalDateTime retryWindowStartedAt;

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

    /** For WhatsApp template sends: {name, language, components}, so a retry resends the
     * template itself rather than the human-readable rendering stored in {@link #content}. */
    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "whatsapp_template", nullable = true)
    private Map<String, Object> whatsappTemplate;

}
