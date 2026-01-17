package com.flexcodelabs.flextuma.core.entities.connector;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.enums.AuthType;
import com.flexcodelabs.flextuma.core.helpers.FieldMapping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "connectorconfig")
public class ConnectorConfig extends Owner {

    public static final String PLURAL = "connectorConfigs";
    public static final String NAME_PLURAL = "Connector Configs";
    public static final String NAME_SINGULAR = "Connector Config";

    public static final String READ = "READ_CONNECTOR_CONFIGS";
    public static final String ADD = "ADD_CONNECTOR_CONFIGS";
    public static final String DELETE = "DELETE_CONNECTOR_CONFIGS";
    public static final String UPDATE = "UPDATE_CONNECTOR_CONFIGS";

    @Column(nullable = false, unique = true, name = "tenantid")
    private String tenantId;

    @Column(nullable = false, name = "url")
    private String url;

    @Column(nullable = false, name = "memberendpoint")
    private String memberEndpoint;

    @Column(name = "searchendpoint", nullable = true)
    private String searchEndpoint;

    @Enumerated(EnumType.STRING)
    private AuthType authType;

    @Column(nullable = true)
    private String token;

    @Column(nullable = true)
    private String username;

    @Column(nullable = true)
    private String password;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FieldMapping> mappings = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        // validate url

        if (authType == AuthType.BASIC && (username == null || password == null)) {
            throw new IllegalStateException("Username and password must be provided for BASIC authentication");
        }

        if (authType == AuthType.BEARER && token == null) {
            throw new IllegalStateException("Token must be provided for BEARER authentication");
        }
    }
}
