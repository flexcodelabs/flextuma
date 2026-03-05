package com.flexcodelabs.flextuma.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class PatAuthenticationFilterTest {

    @Mock
    private PersonalAccessTokenRepository patRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private PatAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_WithValidToken_AuthenticatesUser() throws ServletException, IOException {
        String rawToken = "test-token";
        String hashedToken = hashToken(rawToken);

        User user = new User();
        user.setUsername("testuser");
        user.setRoles(Collections.emptySet());

        PersonalAccessToken pat = new PersonalAccessToken();
        pat.setToken(hashedToken);
        pat.setUser(user);
        pat.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(request.getHeader("X-API-KEY")).thenReturn(rawToken);
        when(patRepository.findByToken(hashedToken)).thenReturn(Optional.of(pat));

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("testuser", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(patRepository).save(pat);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WithInvalidToken_DoesNotAuthenticate() throws ServletException, IOException {
        String rawToken = "invalid-token";
        String hashedToken = hashToken(rawToken);

        when(request.getHeader("X-API-KEY")).thenReturn(rawToken);
        when(patRepository.findByToken(hashedToken)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
