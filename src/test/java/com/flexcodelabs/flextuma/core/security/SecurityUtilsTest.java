package com.flexcodelabs.flextuma.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityUtilsTest {

    @Test
    void getCurrentUserAuthorities_shouldReturnEmpty_whenAuthenticationIsNull() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        Set<String> result = SecurityUtils.getCurrentUserAuthorities();

        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentUserAuthorities_shouldReturnEmpty_whenNotAuthenticated() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        Set<String> result = SecurityUtils.getCurrentUserAuthorities();

        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentUserAuthorities_shouldReturnAuthorities_whenAuthenticated() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("READ_PRIVILEGE"));
        doReturn(authorities).when(auth).getAuthorities();

        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        Set<String> result = SecurityUtils.getCurrentUserAuthorities();

        assertEquals(2, result.size());
        assertTrue(result.contains("ROLE_USER"));
        assertTrue(result.contains("READ_PRIVILEGE"));
    }

    @Test
    void getCurrentUserAuthorities_shouldReturnEmptySet_whenAnonymous() {
        SecurityContext securityContext = mock(SecurityContext.class);
        org.springframework.security.authentication.AnonymousAuthenticationToken anonymous = mock(
                org.springframework.security.authentication.AnonymousAuthenticationToken.class);
        when(securityContext.getAuthentication()).thenReturn(anonymous);
        SecurityContextHolder.setContext(securityContext);

        Set<String> result = SecurityUtils.getCurrentUserAuthorities();

        assertTrue(result != null && result.isEmpty());
    }
}
