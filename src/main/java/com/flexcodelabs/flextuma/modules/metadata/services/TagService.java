package com.flexcodelabs.flextuma.modules.metadata.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.metadata.Tag;
import com.flexcodelabs.flextuma.core.repositories.TagRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagService extends BaseService<Tag> {

    private final TagRepository repository;

    @Override
    protected JpaRepository<Tag, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return Tag.READ;
    }

    @Override
    protected String getAddPermission() {
        return Tag.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return Tag.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return Tag.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return Tag.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return Tag.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return Tag.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<Tag> getRepositoryAsExecutor() {
        return repository;
    }
}
