package com.flexcodelabs.flextuma.modules.auth.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.auth.Privilege;
import com.flexcodelabs.flextuma.core.repositories.PrivilegeRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrivilegeService extends BaseService<Privilege> {

    private final PrivilegeRepository repository;

    @Override
    protected boolean isAdminEntity() {
        return true;
    }

    @Override
    protected JpaRepository<Privilege, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return Privilege.READ;
    }

    @Override
    protected String getAddPermission() {
        return Privilege.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return Privilege.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return Privilege.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return Privilege.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return Privilege.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return Privilege.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<Privilege> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected String getTableName() {
        return "privilege";
    }

    @Override
    protected void validateDelete(Privilege entity) {
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("You cannot delete an active privilege");
        }
    }
}
