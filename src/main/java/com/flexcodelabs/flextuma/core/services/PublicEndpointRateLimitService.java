package com.flexcodelabs.flextuma.core.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.exceptions.RateLimitExceededException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Fixed-window request-volume limiter for unauthenticated public endpoints (no session/user to
 * key off, so this is IP-based like {@link AuthRateLimitService}). Unlike that service -- which
 * only counts failed login/registration attempts and forgives on success -- every call here
 * counts against the caller's window regardless of outcome, since abuse of a public read
 * endpoint (scraping, enumeration) looks like volume, not failures.
 */
@Service
public class PublicEndpointRateLimitService {

    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> windowStarts = new ConcurrentHashMap<>();

    /** Throws {@link RateLimitExceededException} once {@code bucket}'s caller has made more than
     * {@code maxRequests} calls within {@code windowSeconds}, otherwise records this call. */
    public void checkAndRecord(String bucket, HttpServletRequest request, int maxRequests, int windowSeconds) {
        String key = bucket + ":" + clientKey(request);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = windowStarts.get(key);

        if (windowStart == null || windowStart.isBefore(now.minusSeconds(windowSeconds))) {
            windowStarts.put(key, now);
            requestCounts.put(key, new AtomicInteger(1));
            return;
        }

        int count = requestCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (count > maxRequests) {
            long secondsRemaining = windowSeconds - Duration.between(windowStart, now).getSeconds();
            throw new RateLimitExceededException("Too many requests.", Math.max(1, secondsRemaining));
        }
    }

    private String clientKey(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
