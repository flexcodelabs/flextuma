package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.modules.auth.services.PersonalAccessTokenService;

@RestController
@RequestMapping("/api/tokens")
public class PersonalAccessTokenController extends BaseController<PersonalAccessToken, PersonalAccessTokenService> {

    public PersonalAccessTokenController(PersonalAccessTokenService service) {
        super(service);
    }

}
