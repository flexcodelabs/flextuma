package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();
        String username = null;

        if (principal instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else if (principal instanceof String principalName) {
            username = principalName;
        } else if (auth.getName() != null && !auth.getName().isBlank()) {
            username = auth.getName();
        }

        if (username == null || "anonymousUser".equalsIgnoreCase(username)) {
            return Optional.empty();
        }

        return userRepository.findByUsername(username);
    }
}
