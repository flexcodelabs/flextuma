package com.flexcodelabs.flextuma.core.entities.connector;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flexcodelabs.flextuma.core.helpers.MaskingUtil;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.enums.AuthType;
import com.flexcodelabs.flextuma.core.helpers.FieldMapping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "connectorconfig")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorConfig extends Owner {

    public static final String PLURAL = "connectorConfigs";
    public static final String NAME_PLURAL = "Connector Configs";
    public static final String NAME_SINGULAR = "Connector Config";

    public static final String READ = "READ_CONNECTOR_CONFIGS";
    public static final String ADD = "ADD_CONNECTOR_CONFIGS";
    public static final String DELETE = "DELETE_CONNECTOR_CONFIGS";
    public static final String UPDATE = "UPDATE_CONNECTOR_CONFIGS";

    @Column(nullable = false, unique = true, name = "tenantid")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String tenantId;

    @Column(nullable = false, name = "url")
    private String url;

    @Column(nullable = false, name = "endpoint")
    private String endpoint;

    @Column(name = "search", nullable = true)
    private String search;

    @Enumerated(EnumType.STRING)
    private AuthType authType;

    @Column(nullable = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String token;

    @Column(nullable = true, name = "apikey")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    @Column(nullable = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;

    @Column(nullable = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @JsonProperty("tenantId")
    public String getMaskedTenantId() {
        return MaskingUtil.mask(this.tenantId);
    }

    @JsonProperty("token")
    public String getMaskedToken() {
        return MaskingUtil.mask(this.token);
    }

    @JsonProperty("apiKey")
    public String getMaskedApiKey() {
        return MaskingUtil.mask(this.apiKey);
    }

    @JsonProperty("username")
    public String getMaskedUsername() {
        return MaskingUtil.mask(this.username);
    }

    @JsonProperty("password")
    public String getMaskedPassword() {
        return MaskingUtil.mask(this.password);
    }

    @JsonProperty("url")
    public String getMaskedUrl() {
        return MaskingUtil.mask(this.url);
    }

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FieldMapping> mappings = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        if (authType == AuthType.BASIC && (username == null || password == null)) {
            throw new IllegalStateException("Username and password must be provided for BASIC authentication");
        }

        if (authType == AuthType.BEARER && token == null) {
            throw new IllegalStateException("Token must be provided for BEARER authentication");
        }

        if (authType == AuthType.API_KEY && apiKey == null) {
            throw new IllegalStateException("API Key must be provided for API_KEY authentication");
        }
    }
}
