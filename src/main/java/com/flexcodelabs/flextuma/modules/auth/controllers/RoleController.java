package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.auth.Role;
import com.flexcodelabs.flextuma.modules.auth.services.RoleService;

@RestController
@RequestMapping("/api/" + Role.PLURAL)
public class RoleController extends BaseController<Role, RoleService> {
    public RoleController(RoleService service) {
        super(service);
    }
}
