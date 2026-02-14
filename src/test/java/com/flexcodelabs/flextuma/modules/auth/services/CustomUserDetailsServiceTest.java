package com.flexcodelabs.flextuma.modules.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.flexcodelabs.flextuma.core.entities.auth.Privilege;
import com.flexcodelabs.flextuma.core.entities.auth.Role;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_shouldReturnUserDetails_whenUserFound() {
        String username = "testuser";
        User user = new User();
        user.setUsername(username);
        user.setPassword("hashedpassword");

        Privilege privilege = new Privilege();
        privilege.setValue("READ_PRIVILEGE");

        Role role = new Role();
        role.setName("ROLE_USER");
        role.setPrivileges(Set.of(privilege));

        user.setRoles(Set.of(role));

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        UserDetails userDetails = service.loadUserByUsername(username);

        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
        assertEquals("hashedpassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("READ_PRIVILEGE")));
    }

    @Test
    void loadUserByUsername_shouldThrowException_whenUserNotFound() {
        String username = "unknown";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(username));
    }
}
