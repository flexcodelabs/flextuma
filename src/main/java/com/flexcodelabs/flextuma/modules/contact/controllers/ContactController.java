package com.flexcodelabs.flextuma.modules.contact.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.contact.Contact;
import com.flexcodelabs.flextuma.modules.contact.services.ContactService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + Contact.PLURAL)
public class ContactController extends BaseController<Contact, ContactService> {

	public ContactController(ContactService service) {
		super(service);
	}
}
