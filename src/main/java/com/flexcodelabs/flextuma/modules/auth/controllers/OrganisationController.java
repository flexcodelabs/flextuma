package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.modules.auth.services.OrganisationService;

@RestController
@RequestMapping("/api/" + Organisation.PLURAL)
public class OrganisationController extends BaseController<Organisation, OrganisationService> {
    public OrganisationController(OrganisationService service) {
        super(service);
    }
}
