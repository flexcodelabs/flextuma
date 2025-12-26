package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.modules.auth.services.UserService;

@RestController
@RequestMapping("/api/" + User.PLURAL)
public class UserController extends BaseController<User, UserService> {
    public UserController(UserService service) {
        super(service);
    }
}
