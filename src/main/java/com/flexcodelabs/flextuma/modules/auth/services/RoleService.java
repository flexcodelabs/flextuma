package com.flexcodelabs.flextuma.modules.auth.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.auth.Role;
import com.flexcodelabs.flextuma.core.repositories.RoleRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service

@RequiredArgsConstructor
public class RoleService extends BaseService<Role> {
    private final RoleRepository repository;

    @Override
    protected JpaRepository<Role, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return Role.READ;
    }

    @Override
    protected String getAddPermission() {
        return Role.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return Role.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return Role.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return Role.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return Role.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return Role.NAME_SINGULAR;
    }

    @Override
    protected void validateDelete(Role role) {
        if (role.isSystem()) {
            throw new IllegalStateException("System roles cannot be deleted");
        }
    }
}
