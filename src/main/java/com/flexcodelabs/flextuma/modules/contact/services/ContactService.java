package com.flexcodelabs.flextuma.modules.contact.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    protected String getTableName() {
        return "contact";
    }

    @Override
    protected void validateDelete(Contact entity) {
        // Clear relationships before deletion to avoid foreign key constraints
        entity.getLists().clear();
        entity.getTags().clear();

        // Save to clear relationships in join tables
        getRepository().save(entity);
    }

    @Override
    @Transactional
    public java.util.Map<String, String> delete(UUID id) {
        checkPermission(getDeletePermission());

        Contact entity = getRepository().findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, getEntitySingular() + " not found"));

        validateDelete(entity);

        // Use native query to force deletion
        entityManager.createNativeQuery("DELETE FROM contact WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();

        entityManager.flush();

        onPostDelete(id);

        return java.util.Map.of("message", getEntitySingular() + " deleted successfully");
    }
}
