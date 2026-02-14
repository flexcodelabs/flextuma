package com.flexcodelabs.flextuma.modules.contact.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.contact.Contact;
import com.flexcodelabs.flextuma.modules.contact.services.ContactService;

public class ContactControllerTest extends BaseControllerTest<Contact, ContactService> {

    @Mock
    private ContactService service;

    private ContactController controller;

    @Override
    protected BaseController<Contact, ContactService> getController() {
        if (controller == null) {
            controller = new ContactController(service);
        }
        return controller;
    }

    @Override
    protected ContactService getService() {
        return service;
    }

    @Override
    protected Contact createEntity() {
        Contact contact = new Contact();
        contact.setFirstName("John");
        contact.setSurname("Doe");
        return contact;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/contacts";
    }
}
