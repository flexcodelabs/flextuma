package com.flexcodelabs.flextuma.modules.contact.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.contact.Contact;
import com.flexcodelabs.flextuma.core.repositories.ContactRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContactService extends BaseService<Contact> {

    private final ContactRepository repository;

    @Override
    protected JpaRepository<Contact, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return Contact.READ;
    }

    @Override
    protected String getAddPermission() {
        return Contact.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return Contact.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return Contact.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return Contact.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return Contact.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return Contact.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<Contact> getRepositoryAsExecutor() {
        return repository;
    }
}
