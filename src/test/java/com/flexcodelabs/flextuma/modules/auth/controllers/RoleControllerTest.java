package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.auth.Role;
import com.flexcodelabs.flextuma.modules.auth.services.RoleService;

public class RoleControllerTest extends BaseControllerTest<Role, RoleService> {

    @Mock
    private RoleService service;

    private RoleController controller;

    @Override
    protected BaseController<Role, RoleService> getController() {
        if (controller == null) {
            controller = new RoleController(service);
        }
        return controller;
    }

    @Override
    protected RoleService getService() {
        return service;
    }

    @Override
    protected Role createEntity() {
        Role role = new Role();
        role.setName("TEST_ROLE");
        return role;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/roles";
    }
}
