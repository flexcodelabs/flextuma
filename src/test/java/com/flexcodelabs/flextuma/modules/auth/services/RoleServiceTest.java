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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.flexcodelabs.flextuma.core.entities.auth.Role;
import com.flexcodelabs.flextuma.core.repositories.RoleRepository;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository repository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(repository);

        securityContextHolderMock = Mockito.mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    @Test
    void delete_shouldThrowException_whenRoleIsSystem() {
        mockPermissions(Set.of(Role.DELETE));
        UUID id = UUID.randomUUID();
        Role role = new Role();
        role.setId(id);
        role.setSystem(true);

        when(repository.findById(id)).thenReturn(Optional.of(role));

        assertThrows(IllegalStateException.class, () -> service.delete(id));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_shouldDelete_whenRoleIsNotSystem() {
        mockPermissions(Set.of(Role.DELETE));
        UUID id = UUID.randomUUID();
        Role role = new Role();
        role.setId(id);
        role.setSystem(false);

        when(repository.findById(id)).thenReturn(Optional.of(role));

        service.delete(id);

        verify(repository).deleteById(id);
    }

    private void mockPermissions(Set<String> permissions) {
        when(authentication.isAuthenticated()).thenReturn(true);
        List<org.springframework.security.core.GrantedAuthority> authorities = permissions.stream()
                .map(p -> (org.springframework.security.core.GrantedAuthority) () -> p)
                .toList();
        doReturn(authorities).when(authentication).getAuthorities();
    }
}
