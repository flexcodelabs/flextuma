package com.flexcodelabs.flextuma.core.entities.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account's data retention choices. Scoped to the organisation when the user has one (shared
 * by every member), otherwise to the individual user -- the same tenant boundary
 * TenantAwareSpecification uses. Exactly one of organisation/owner is set.
 */
@Entity
@Table(name = "data_retention_policy")
@Getter
@Setter
@NoArgsConstructor
public class DataRetentionPolicy extends BaseEntity {

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation", unique = true)
    private Organisation organisation;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", unique = true)
    private User owner;

    /** Days to keep the WhatsApp event log. Null = platform default. */
    @Column(name = "whatsapp_event_retention_days")
    private Integer whatsappEventRetentionDays;
}
