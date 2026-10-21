package com.flexcodelabs.flextuma.core.entities.whatsapp;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One signature-valid change from a Meta webhook delivery (a single delivery can carry several),
 * recorded by {@code WhatsAppWebhookController} for every event type -- messages, statuses,
 * template updates, account alerts, etc. -- whether or not the config relays to a callback URL.
 * Written only by the webhook, so {@link #ADD}/{@link #UPDATE}/{@link #DELETE} require a
 * permission no tenant role is granted (see {@code WhatsAppWebhookEventService}).
 */
@Entity
@Table(name = "whatsapp_webhook_event", indexes = {
        @Index(name = "idx_whatsapp_webhook_event_received_at", columnList = "received_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WhatsAppWebhookEvent extends Owner {
    public static final String PLURAL = "whatsAppWebhookEvents";
    public static final String NAME_PLURAL = "WhatsApp Events";
    public static final String NAME_SINGULAR = "WhatsApp Event";
    public static final String READ = "ALL";
    public static final String ADD = "ADD_WHATSAPP_WEBHOOK_EVENTS";
    public static final String DELETE = "DELETE_WHATSAPP_WEBHOOK_EVENTS";
    public static final String UPDATE = "UPDATE_WHATSAPP_WEBHOOK_EVENTS";

    /** Cascades at the DB level: the event log belongs to its config, and BaseService#delete removes
     * configs with a native DELETE that would otherwise be blocked by this FK. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WhatsAppWebhookConfig config;

    /** Meta's change field, e.g. "messages", "message_template_status_update", "account_alerts". */
    @Column(nullable = false)
    private String field;

    /** Derived kind, e.g. "message.received", "status.delivered", "template.APPROVED". */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(length = 500)
    private String summary;

    /** The raw change object ({"field": ..., "value": ...}) as JSON. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
