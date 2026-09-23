package com.flexcodelabs.flextuma.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;
import com.flexcodelabs.flextuma.core.services.AuthRateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class PatAuthenticationFilterTest {

    @Mock
    private PersonalAccessTokenRepository patRepository;

    @Mock
    private AuthRateLimitService rateLimitService;

    @Mock
    private ObjectMapper objectMapper;

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
        verify(rateLimitService).recordSuccessfulAttempt(request, "PAT");
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
        verify(rateLimitService).recordFailedAttempt(request, "PAT");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WhenRateLimited_RejectsWithoutLookup() throws ServletException, IOException {
        when(request.getHeader("X-API-KEY")).thenReturn("some-token");
        when(rateLimitService.isBlocked(request, "PAT")).thenReturn(true);
        when(rateLimitService.getBlockTimeRemainingSeconds(request, "PAT")).thenReturn(42L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(response.getWriter()).thenReturn(new PrintWriter(java.io.Writer.nullWriter()));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", "42");
        verify(patRepository, never()).findByToken(anyString());
        verify(filterChain, never()).doFilter(request, response);
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
