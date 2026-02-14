package com.flexcodelabs.flextuma.core.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;

@ExtendWith(MockitoExtension.class)
class AuditorAwareImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private AuditorAwareImpl auditorAware;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void getCurrentAuditor_shouldReturnEmpty_whenAuthenticationIsNull() {
        when(securityContext.getAuthentication()).thenReturn(null);

        Optional<User> result = auditorAware.getCurrentAuditor();

        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentAuditor_shouldReturnEmpty_whenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(auth);

        Optional<User> result = auditorAware.getCurrentAuditor();

        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentAuditor_shouldReturnEmpty_whenAnonymous() {
        AnonymousAuthenticationToken auth = mock(AnonymousAuthenticationToken.class);
        when(securityContext.getAuthentication()).thenReturn(auth);

        Optional<User> result = auditorAware.getCurrentAuditor();

        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentAuditor_shouldReturnUser_whenAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("testuser");
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(securityContext.getAuthentication()).thenReturn(auth);

        User user = new User();
        when(entityManager.getFlushMode()).thenReturn(FlushModeType.AUTO);
        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(user));

        Optional<User> result = auditorAware.getCurrentAuditor();

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(entityManager).setFlushMode(FlushModeType.COMMIT);
        verify(entityManager).setFlushMode(FlushModeType.AUTO);
    }

    @Test
    void getCurrentAuditor_shouldPropagateException_andExecuteFinally_whenExceptionOccursInTry() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("testuser");
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(securityContext.getAuthentication()).thenReturn(auth);

        when(entityManager.getFlushMode()).thenReturn(FlushModeType.AUTO);
        when(userRepository.findByIdentifier("testuser")).thenThrow(new RuntimeException("DB Error"));

        assertThrows(RuntimeException.class, () -> auditorAware.getCurrentAuditor());

        verify(entityManager).setFlushMode(FlushModeType.COMMIT);
        verify(entityManager).setFlushMode(FlushModeType.AUTO);
    }
}
