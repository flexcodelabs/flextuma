package com.flexcodelabs.flextuma.modules.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository);

        securityContextHolderMock = Mockito.mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    @Test
    void login_shouldReturnUser_whenCredentialsAreValid() {
        String username = "testuser";
        String password = "password";
        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPassword);

        when(repository.findByUsername(username)).thenReturn(Optional.of(user));

        User result = service.login(username, password);

        assertNotNull(result);
        assertEquals(username, result.getUsername());
    }

    @Test
    void login_shouldThrowException_whenPasswordInvalid() {
        String username = "testuser";
        String password = "password";
        String wrongPassword = "wrong";
        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPassword);

        when(repository.findByUsername(username)).thenReturn(Optional.of(user));

        assertThrows(ResponseStatusException.class, () -> service.login(username, wrongPassword));
    }

    @Test
    void login_shouldThrowException_whenUserNotFound() {
        String username = "unknown";
        when(repository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.login(username, "password"));
    }

    @Test
    void delete_shouldThrowException_whenUserIsSystem() {
        mockPermissions(Set.of(User.DELETE));
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setSystem(true);

        when(repository.findById(id)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () -> service.delete(id));
        verify(repository, never()).deleteById(any());
    }

    private void mockPermissions(Set<String> permissions) {
        when(authentication.isAuthenticated()).thenReturn(true);
        List<org.springframework.security.core.GrantedAuthority> authorities = permissions.stream()
                .map(p -> (org.springframework.security.core.GrantedAuthority) () -> p)
                .toList();
        doReturn(authorities).when(authentication).getAuthorities();
    }
}
