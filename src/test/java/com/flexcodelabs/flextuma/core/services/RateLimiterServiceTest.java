package com.flexcodelabs.flextuma.core.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    @Test
    void testCheckRateLimitAllowsRequestsWithinLimit() {
        RateLimiterService rateLimiterService = new RateLimiterService();
        UUID tenantId = UUID.randomUUID();

        // 10 requests should be allowed
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> rateLimiterService.checkRateLimit(tenantId));
        }
    }

    @Test
    void testCheckRateLimitThrowsWhenExceeded() {
        RateLimiterService rateLimiterService = new RateLimiterService();
        UUID tenantId = UUID.randomUUID();

        // Consume all 10 tokens
        for (int i = 0; i < 10; i++) {
            rateLimiterService.checkRateLimit(tenantId);
        }

        // 11th request should throw Exception
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> rateLimiterService.checkRateLimit(tenantId));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Rate limit exceeded"));
    }

    @Test
    void testCheckRateLimitIgnoresNullTenantId() {
        RateLimiterService rateLimiterService = new RateLimiterService();
        assertDoesNotThrow(() -> rateLimiterService.checkRateLimit(null));
    }
}
