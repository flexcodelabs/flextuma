package com.flexcodelabs.flextuma.modules.auth.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service

@RequiredArgsConstructor
public class UserService extends BaseService<User> {
    private final UserRepository repository;

    @Override
    protected JpaRepository<User, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return User.READ;
    }

    @Override
    protected String getAddPermission() {
        return User.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return User.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return User.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return User.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return User.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return User.NAME_SINGULAR;
    }

    @Override
    protected JpaSpecificationExecutor<User> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected void validateDelete(User user) {
        if (user.getSystem()) {
            throw new IllegalStateException("System users cannot be deleted");
        }
    }

    public User login(String username, String password) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invalid username or password"));
        if (!user.validatePassword(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return user;
    }

}
