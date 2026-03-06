package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CurrentUserResolver currentUserResolver;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_withNoAuthentication_shouldReturnEmpty() {
        when(securityContext.getAuthentication()).thenReturn(null);
        Optional<User> result = currentUserResolver.getCurrentUser();
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentUser_notAuthenticated_shouldReturnEmpty() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        Optional<User> result = currentUserResolver.getCurrentUser();
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentUser_principalNotString_shouldReturnEmpty() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new Object());
        Optional<User> result = currentUserResolver.getCurrentUser();
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentUser_successful_shouldReturnUser() {
        String username = "testuser";
        User user = new User();
        user.setUsername(username);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        Optional<User> result = currentUserResolver.getCurrentUser();
        assertTrue(result.isPresent());
        assertEquals(username, result.get().getUsername());
    }
}
