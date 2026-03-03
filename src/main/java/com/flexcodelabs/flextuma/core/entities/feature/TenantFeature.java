package com.flexcodelabs.flextuma.core.entities.feature;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;

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

@Entity
@Table(name = "tenantfeature", uniqueConstraints = {
        @UniqueConstraint(name = "unique_org_feature", columnNames = { "organisation", "featurekey" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantFeature extends BaseEntity {

    public static final String PLURAL = "tenantFeatures";
    public static final String NAME_PLURAL = "Tenant Features";
    public static final String NAME_SINGULAR = "Tenant Feature";

    public static final String READ = "READ_TENANT_FEATURES";
    public static final String ADD = "ADD_TENANT_FEATURES";
    public static final String DELETE = "DELETE_TENANT_FEATURES";
    public static final String UPDATE = "UPDATE_TENANT_FEATURES";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation", nullable = false)
    private Organisation organisation;

    @NotBlank(message = "Feature key is required")
    @Column(name = "featurekey", nullable = false)
    private String featureKey;

    @Column(nullable = false)
    private Boolean enabled = true;
}
