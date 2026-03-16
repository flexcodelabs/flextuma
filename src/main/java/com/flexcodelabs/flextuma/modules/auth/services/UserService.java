package com.flexcodelabs.flextuma.modules.auth.services;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.dtos.RegisterDto;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service

@RequiredArgsConstructor
public class UserService extends BaseService<User> {
    private final UserRepository repository;

    @Override
    protected boolean isAdminEntity() {
        return true;
    }

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
    protected String getTableName() {
        return "\"user\"";
    }

    @Override
    protected JpaSpecificationExecutor<User> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected void validateDelete(User user) {
        if (Boolean.TRUE.equals(user.getSystem())) {
            throw new IllegalStateException("System users cannot be deleted");
        }
    }

    public User login(String username, String password) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Invalid username or password"));
        if (!user.validatePassword(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return user;
    }

    public AuthenticationResult authenticateAndCreateContext(String username, String password,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = login(username, password);

        java.util.Set<SimpleGrantedAuthority> authorities = user.getRoles()
                .stream()
                .flatMap(role -> role.getPrivileges().stream())
                .map(privilege -> new SimpleGrantedAuthority(privilege.getValue()))
                .collect(java.util.stream.Collectors.toSet());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, authorities);
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(httpRequest));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return new AuthenticationResult(user, authentication);
    }

    public User findByUsername(String username) {
        return repository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User with username " + username + " not found"));
    }

    public User register(RegisterDto request) {
        repository.findByUsername(request.getUsername()).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User with username " + request.getUsername() + " already exists");
        });

        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());

        return repository.save(user);
    }

    public void changePassword(User user, String newPassword) {
        user.setPassword(newPassword);
        user.setChangePassword(false);
        repository.save(user);
    }

}
