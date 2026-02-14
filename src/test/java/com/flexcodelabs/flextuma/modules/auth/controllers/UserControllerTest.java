package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.modules.auth.services.UserService;

public class UserControllerTest extends BaseControllerTest<User, UserService> {

    @Mock
    private UserService service;

    private UserController controller;

    @Override
    protected BaseController<User, UserService> getController() {
        if (controller == null) {
            controller = new UserController(service);
        }
        return controller;
    }

    @Override
    protected UserService getService() {
        return service;
    }

    @Override
    protected User createEntity() {
        User user = new User();
        user.setUsername("testuser");
        user.setName("Test User");
        user.setPassword("password");
        user.setPhoneNumber("1234567890");
        return user;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/users";
    }
}
