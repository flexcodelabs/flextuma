package com.flexcodelabs.flextuma.modules.contact.services;

import com.flexcodelabs.flextuma.core.entities.contact.Contact;
import com.flexcodelabs.flextuma.core.repositories.ContactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository repository;

    @InjectMocks
    private ContactService service;

    @Test
    void getRepository_shouldReturnRepository() {
        assertEquals(repository, service.getRepository());
    }

    @Test
    void getRepositoryAsExecutor_shouldReturnRepository() {
        assertEquals(repository, service.getRepositoryAsExecutor());
    }

    @Test
    void getPermissions_shouldReturnCorrectValues() {
        assertEquals(Contact.READ, service.getReadPermission());
        assertEquals(Contact.ADD, service.getAddPermission());
        assertEquals(Contact.UPDATE, service.getUpdatePermission());
        assertEquals(Contact.DELETE, service.getDeletePermission());
    }

    @Test
    void getEntityNames_shouldReturnCorrectValues() {
        assertEquals(Contact.NAME_PLURAL, service.getEntityPlural());
        assertEquals(Contact.NAME_SINGULAR, service.getEntitySingular());
        assertEquals(Contact.PLURAL, service.getPropertyName());
    }
}
