package com.flexcodelabs.flextuma.modules.auth.services;

import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.repositories.OrganisationRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganisationService extends BaseService<Organisation> {

    private final OrganisationRepository repository;

    @Override
    protected boolean isAdminEntity() {
        return true;
    }

    @Override
    protected JpaRepository<Organisation, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return Organisation.READ;
    }

    @Override
    protected String getAddPermission() {
        return Organisation.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return Organisation.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return Organisation.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return Organisation.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return Organisation.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return Organisation.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<Organisation> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected String getTableName() {
        return "organisation";
    }

    @Override
    protected void validateDelete(Organisation entity) {
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("You cannot delete an active organisation");
        }
    }
}
