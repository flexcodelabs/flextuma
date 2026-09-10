package com.flexcodelabs.flextuma.core.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flexcodelabs.flextuma.core.exceptions.RateLimitExceededException;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class PublicEndpointRateLimitServiceTest {

    @Mock
    private HttpServletRequest request;

    private final PublicEndpointRateLimitService service = new PublicEndpointRateLimitService();

    @Test
    void checkAndRecord_shouldAllowCallsUpToTheLimit() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> service.checkAndRecord("bucket", request, 5, 60));
        }
    }

    @Test
    void checkAndRecord_shouldThrow_onceLimitExceededWithinWindow() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");

        for (int i = 0; i < 5; i++) {
            service.checkAndRecord("bucket", request, 5, 60);
        }

        assertThrows(RateLimitExceededException.class, () -> service.checkAndRecord("bucket", request, 5, 60));
    }

    @Test
    void checkAndRecord_shouldTrackBucketsIndependently_forTheSameClient() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.3");

        for (int i = 0; i < 5; i++) {
            service.checkAndRecord("bucket-a", request, 5, 60);
        }

        // A different bucket for the same IP must not be affected by bucket-a's exhausted limit.
        assertDoesNotThrow(() -> service.checkAndRecord("bucket-b", request, 5, 60));
    }

    @Test
    void checkAndRecord_shouldTrackClientsIndependently_forTheSameBucket() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        for (int i = 0; i < 5; i++) {
            service.checkAndRecord("bucket", request, 5, 60);
        }

        // A different client IP must not be affected by the first client's exhausted limit.
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        assertDoesNotThrow(() -> service.checkAndRecord("bucket", request, 5, 60));
    }

    @Test
    void checkAndRecord_shouldPreferForwardedForHeader_overRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");

        for (int i = 0; i < 5; i++) {
            service.checkAndRecord("bucket", request, 5, 60);
        }

        assertThrows(RateLimitExceededException.class, () -> service.checkAndRecord("bucket", request, 5, 60));
    }
}
