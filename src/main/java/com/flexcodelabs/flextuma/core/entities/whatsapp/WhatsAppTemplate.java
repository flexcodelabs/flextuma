package com.flexcodelabs.flextuma.core.entities.whatsapp;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Mirrors a Meta-approved WhatsApp Business template. Synced from the Graph API by
 * {@code WhatsAppTemplateSyncService} and kept current by the webhook's
 * {@code message_template_status_update} handler -- never hand-authored by a tenant, which is why
 * {@link #ADD}/{@link #UPDATE}/{@link #DELETE} require a permission no ordinary tenant role is
 * granted (see {@code WhatsAppTemplateService}).
 */
@Entity
@Table(name = "whatsapp_template", uniqueConstraints = {
        @UniqueConstraint(name = "unique_meta_template_id", columnNames = { "meta_template_id", "creator" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppTemplate extends Owner {
    public static final String PLURAL = "whatsappTemplates";
    public static final String NAME_PLURAL = "WhatsApp Templates";
    public static final String NAME_SINGULAR = "WhatsApp Template";

    public static final String READ = "ALL";
    public static final String ADD = "ADD_WHATSAPP_TEMPLATES";
    public static final String UPDATE = "UPDATE_WHATSAPP_TEMPLATES";
    public static final String DELETE = "DELETE_WHATSAPP_TEMPLATES";

    /** REMOVED marks a template Meta no longer returns on sync, so a send configuration
     * referencing it fails validation instead of dangling on a deleted row. */
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_APPROVED = "APPROVED";

    @NotBlank
    @Column(name = "meta_template_id", nullable = false)
    private String metaTemplateId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String category;

    @NotBlank
    @Column(nullable = false)
    private String language;

    private String status;

    /** Raw components array Meta returned (HEADER/BODY/BUTTONS), as JSON text -- kept as TEXT,
     * not a native jsonb type, to match Hibernate's unattended ddl-auto=update schema management
     * (no migration framework yet; see docs/third-party-integration.md). */
    @Column(columnDefinition = "TEXT")
    private String componentsJson;

    /** Derived {{n}} placeholder list (component/type/position), as JSON text. */
    @Column(columnDefinition = "TEXT")
    private String placeholdersJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connector", nullable = false)
    private SmsConnector connector;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
