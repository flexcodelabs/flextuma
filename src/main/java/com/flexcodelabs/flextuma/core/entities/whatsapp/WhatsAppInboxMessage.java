package com.flexcodelabs.flextuma.core.entities.whatsapp;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_inbox_message", uniqueConstraints = @UniqueConstraint(name = "uk_whatsapp_inbox_provider_message_id", columnNames = "provider_message_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WhatsAppInboxMessage extends Owner {
    public static final String PLURAL = "whatsAppInboxMessages";
    public static final String NAME_PLURAL = "WhatsApp Inbox Messages";
    public static final String NAME_SINGULAR = "WhatsApp Inbox Message";
    public static final String READ = "READ_WHATSAPP_INBOX_MESSAGES";
    public static final String ADD = "ADD_WHATSAPP_INBOX_MESSAGES";
    public static final String DELETE = "DELETE_WHATSAPP_INBOX_MESSAGES";
    public static final String UPDATE = "UPDATE_WHATSAPP_INBOX_MESSAGES";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config", nullable = false)
    private WhatsAppWebhookConfig config;

    @Column(name = "from_number", nullable = false)
    private String fromNumber;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "provider_message_id", nullable = false, unique = true)
    private String providerMessageId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** Unread when null. */
    @Column(name = "read_at")
    private LocalDateTime readAt;
}
