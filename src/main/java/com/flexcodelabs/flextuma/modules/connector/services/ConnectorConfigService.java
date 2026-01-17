package com.flexcodelabs.flextuma.modules.connector.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.core.repositories.ConnectorConfigRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConnectorConfigService extends BaseService<ConnectorConfig> {

    private final ConnectorConfigRepository repository;

    @Override
    protected JpaRepository<ConnectorConfig, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return ConnectorConfig.READ;
    }

    @Override
    protected String getAddPermission() {
        return ConnectorConfig.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return ConnectorConfig.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return ConnectorConfig.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return ConnectorConfig.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return ConnectorConfig.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return ConnectorConfig.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<ConnectorConfig> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected void validateDelete(ConnectorConfig entity) {
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("Cannot delete an active config");
        }
    }
}