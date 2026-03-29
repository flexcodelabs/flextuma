package com.flexcodelabs.flextuma.modules.contact.services;

import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.auth.Role;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.contact.Contact;
import com.flexcodelabs.flextuma.core.entities.metadata.AbstractMetadataEntity;
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
    @Transactional(readOnly = true)
    public Pagination<Contact> findAllPaginated(Pageable pageable, List<String> filter, String fields, String rootJoin) {
        Pagination<Contact> pagination = super.findAllPaginated(pageable, filter, fields, rootJoin);
        pagination.getData().forEach(this::initializeForSerialization);
        return pagination;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contact> findAll(String fields, List<String> filter, String rootJoin) {
        List<Contact> contacts = super.findAll(fields, filter, rootJoin);
        contacts.forEach(this::initializeForSerialization);
        return contacts;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Contact> findById(UUID id, String fields) {
        java.util.Optional<Contact> contact = super.findById(id, fields);
        contact.ifPresent(this::initializeForSerialization);
        return contact;
    }

    @Override
    @Transactional
    public Contact save(Contact entity) {
        Contact saved = super.save(entity);
        initializeForSerialization(saved);
        return saved;
    }

    @Override
    @Transactional
    public Contact update(UUID id, Contact entity) {
        Contact updated = super.update(id, entity);
        initializeForSerialization(updated);
        return updated;
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

    private void initializeForSerialization(Contact contact) {
        Hibernate.initialize(contact.getLists());
        Hibernate.initialize(contact.getTags());
        initializeUser(contact.getCreatedBy());
        initializeUser(contact.getUpdatedBy());

        contact.getLists().forEach(this::initializeMetadataEntity);
        contact.getTags().forEach(this::initializeMetadataEntity);
    }

    private void initializeMetadataEntity(AbstractMetadataEntity entity) {
        initializeUser(entity.getCreatedBy());
        initializeUser(entity.getUpdatedBy());
    }

    private void initializeUser(User user) {
        if (user == null) {
            return;
        }

        Hibernate.initialize(user);
        Hibernate.initialize(user.getRoles());
        for (Role role : user.getRoles()) {
            Hibernate.initialize(role.getPrivileges());
        }
    }
}
