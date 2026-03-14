package com.flexcodelabs.flextuma.modules.auth.services;

import org.springframework.security.core.Authentication;
import com.flexcodelabs.flextuma.core.entities.auth.User;

public record AuthenticationResult(User user, Authentication authentication) {
}
