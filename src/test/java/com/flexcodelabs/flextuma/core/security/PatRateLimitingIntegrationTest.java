package com.flexcodelabs.flextuma.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;
import com.flexcodelabs.flextuma.core.services.AuthRateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exercises the real AuthRateLimitService (no mocking of the counting/blocking logic itself)
 * to prove PAT auth actually gets throttled after repeated bad X-API-KEY values from one IP,
 * and that the "PAT" scope is isolated from other AuthRateLimitService callers (e.g. login).
 */
class PatRateLimitingIntegrationTest {

    private PersonalAccessTokenRepository patRepository;
    private AuthRateLimitService rateLimitService;
    private PatAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        patRepository = mock(PersonalAccessTokenRepository.class);
        when(patRepository.findByToken(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        rateLimitService = new AuthRateLimitService(); // real instance, no Spring container here so @Value fields need manual setting
        ReflectionTestUtils.setField(rateLimitService, "maxAttempts", 5);
        ReflectionTestUtils.setField(rateLimitService, "blockDurationMinutes", 15);
        ReflectionTestUtils.setField(rateLimitService, "windowMinutes", 5);
        // mirrors JacksonConfig.objectMapper()'s findAndAddModules(), which registers JavaTimeModule for LocalDateTime
        filter = new PatAuthenticationFilter(patRepository, rateLimitService, new ObjectMapper().findAndRegisterModules());
        filterChain = mock(FilterChain.class);
    }

    private HttpServletRequest requestFrom(String ip, String apiKey) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-KEY")).thenReturn(apiKey);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);
        return request;
    }

    private int fireAndGetStatus(String ip, String apiKey) throws ServletException, IOException {
        HttpServletRequest request = requestFrom(ip, apiKey);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        int[] status = { 200 }; // default: nothing set means the request fell through to filterChain
        org.mockito.Mockito.doAnswer(inv -> {
            status[0] = inv.getArgument(0);
            return null;
        }).when(response).setStatus(org.mockito.ArgumentMatchers.anyInt());

        filter.doFilterInternal(request, response, filterChain);
        return status[0];
    }

    @Test
    void sixthBadKeyFromSameIpIsRateLimited() throws Exception {
        String ip = "203.0.113.10";

        // Attempts 1-5: each is an invalid key, none should be rate-limited yet.
        for (int i = 1; i <= 5; i++) {
            int status = fireAndGetStatus(ip, "ft_bad" + i);
            assertEquals(200, status, "attempt " + i + " should not be rate-limited yet");
        }
        verify(filterChain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // 6th attempt from the same IP: rate limiter should now reject with 429, before any DB lookup.
        int sixthStatus = fireAndGetStatus(ip, "ft_bad6");
        assertEquals(429, sixthStatus);

        // filterChain must NOT have advanced for the 6th (blocked) request.
        verify(filterChain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void differentIpIsNotAffectedByAnotherIpsFailures() throws Exception {
        String attackerIp = "203.0.113.20";
        String innocentIp = "198.51.100.5";

        for (int i = 1; i <= 6; i++) {
            fireAndGetStatus(attackerIp, "ft_bad" + i);
        }
        assertTrue(rateLimitService.isBlocked(requestFrom(attackerIp, null), "PAT"));

        int innocentStatus = fireAndGetStatus(innocentIp, "ft_still_bad");
        assertEquals(200, innocentStatus, "a different IP must not be blocked by another IP's failures");
        assertFalse(rateLimitService.isBlocked(requestFrom(innocentIp, null), "PAT"));
    }

    @Test
    void patScopeIsIsolatedFromAuthScope() throws Exception {
        String ip = "203.0.113.30";
        HttpServletRequest sharedIpRequest = requestFrom(ip, null);

        // Simulate 5 failed *login* attempts (AuthController's default "AUTH" scope) from this IP.
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordFailedAttempt(sharedIpRequest); // defaults to "AUTH" scope
        }
        assertTrue(rateLimitService.isBlocked(sharedIpRequest), "AUTH scope should now be blocked");

        // A PAT request from the very same IP must be unaffected, since it's a different scope.
        int patStatus = fireAndGetStatus(ip, "ft_unrelated_bad_key");
        assertEquals(200, patStatus, "PAT scope must be independent of the AUTH scope's block");
    }
}
