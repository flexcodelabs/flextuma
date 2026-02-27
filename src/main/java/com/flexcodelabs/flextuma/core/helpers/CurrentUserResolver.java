package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the full User entity (including organisation) from the security
 * context.
 * The standard Spring UserDetails only stores username + authorities — we need
 * the
 * DB entity to access organisation membership and other domain fields.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof String username)) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username);
    }
}
