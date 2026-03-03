package com.flexcodelabs.flextuma.modules.feature.services;

import com.flexcodelabs.flextuma.core.entities.feature.TenantFeature;
import com.flexcodelabs.flextuma.core.repositories.TenantFeatureRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantFeatureService extends BaseService<TenantFeature> {

    private final TenantFeatureRepository repository;

    @Override
    protected JpaRepository<TenantFeature, UUID> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<TenantFeature> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return TenantFeature.READ;
    }

    @Override
    protected String getAddPermission() {
        return TenantFeature.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return TenantFeature.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return TenantFeature.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return TenantFeature.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return TenantFeature.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return TenantFeature.NAME_SINGULAR;
    }
}
