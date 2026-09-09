package com.flexcodelabs.flextuma.core.entities.whatsapp;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_relay_delivery")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class WhatsAppRelayDelivery extends Owner {
    public static final String PLURAL = "whatsAppRelayDeliveries";
    public static final String NAME_PLURAL = "WhatsApp Relay Deliveries";
    public static final String NAME_SINGULAR = "WhatsApp Relay Delivery";
    public static final String READ = "READ_WHATSAPP_RELAY_DELIVERIES";
    public static final String ADD = "ADD_WHATSAPP_RELAY_DELIVERIES";
    public static final String DELETE = "DELETE_WHATSAPP_RELAY_DELIVERIES";
    public static final String UPDATE = "UPDATE_WHATSAPP_RELAY_DELIVERIES";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config", nullable = false)
    private WhatsAppWebhookConfig config;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WhatsAppRelayStatus status = WhatsAppRelayStatus.PENDING;

    @Column(nullable = false)
    private int retries = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
