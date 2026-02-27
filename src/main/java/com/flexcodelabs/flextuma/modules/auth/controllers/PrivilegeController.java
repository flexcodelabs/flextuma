package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.springframework.web.bind.annotation.*;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.auth.Privilege;
import com.flexcodelabs.flextuma.modules.auth.services.PrivilegeService;

@RestController
@RequestMapping("/api/" + Privilege.PLURAL)
public class PrivilegeController extends BaseController<Privilege, PrivilegeService> {
    public PrivilegeController(PrivilegeService service) {
        super(service);
    }
}
