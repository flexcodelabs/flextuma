package com.flexcodelabs.flextuma.modules.metadata.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.metadata.ListEntity;
import com.flexcodelabs.flextuma.core.repositories.ListRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListService extends BaseService<ListEntity> {

    private final ListRepository repository;

    @Override
    protected JpaRepository<ListEntity, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return ListEntity.READ;
    }

    @Override
    protected String getAddPermission() {
        return ListEntity.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return ListEntity.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return ListEntity.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return ListEntity.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return ListEntity.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return ListEntity.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<ListEntity> getRepositoryAsExecutor() {
        return repository;
    }
}
