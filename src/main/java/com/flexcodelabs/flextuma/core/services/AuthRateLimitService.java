package com.flexcodelabs.flextuma.core.services;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthRateLimitService {

    private final ConcurrentHashMap<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> blockTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastAttemptTimes = new ConcurrentHashMap<>();

    @Value("${flextuma.auth.max-attempts:5}")
    private int maxAttempts;

    @Value("${flextuma.auth.block-duration-minutes:15}")
    private int blockDurationMinutes;

    @Value("${flextuma.auth.window-minutes:5}")
    private int windowMinutes;

    public boolean isBlocked(HttpServletRequest request) {
        String clientKey = getClientKey(request);

        // Check if client is currently blocked
        LocalDateTime blockEndTime = blockTimestamps.get(clientKey);
        if (blockEndTime != null && LocalDateTime.now().isBefore(blockEndTime)) {
            return true;
        }

        // Clear expired block
        if (blockEndTime != null && LocalDateTime.now().isAfter(blockEndTime)) {
            blockTimestamps.remove(clientKey);
            attemptCounts.remove(clientKey);
            lastAttemptTimes.remove(clientKey);
        }

        return false;
    }

    public void recordFailedAttempt(HttpServletRequest request) {
        String clientKey = getClientKey(request);
        LocalDateTime now = LocalDateTime.now();

        // Clean up old attempts outside the window
        LocalDateTime windowStart = now.minusMinutes(windowMinutes);
        LocalDateTime lastAttempt = lastAttemptTimes.get(clientKey);

        if (lastAttempt == null || lastAttempt.isBefore(windowStart)) {
            // Reset counter if outside window
            attemptCounts.put(clientKey, new AtomicInteger(1));
        } else {
            // Increment counter
            attemptCounts.computeIfAbsent(clientKey, k -> new AtomicInteger(0)).incrementAndGet();
        }

        lastAttemptTimes.put(clientKey, now);

        // Check if should block
        int attempts = attemptCounts.get(clientKey).get();
        if (attempts >= maxAttempts) {
            LocalDateTime blockEndTime = now.plusMinutes(blockDurationMinutes);
            blockTimestamps.put(clientKey, blockEndTime);
        }
    }

    public void recordSuccessfulAttempt(HttpServletRequest request) {
        String clientKey = getClientKey(request);

        // Clear all tracking on successful attempt
        attemptCounts.remove(clientKey);
        blockTimestamps.remove(clientKey);
        lastAttemptTimes.remove(clientKey);
    }

    public int getRemainingAttempts(HttpServletRequest request) {
        String clientKey = getClientKey(request);
        AtomicInteger attempts = attemptCounts.get(clientKey);
        if (attempts == null)
            return maxAttempts;

        return Math.max(0, maxAttempts - attempts.get());
    }

    public long getBlockTimeRemainingSeconds(HttpServletRequest request) {
        String clientKey = getClientKey(request);
        LocalDateTime blockEndTime = blockTimestamps.get(clientKey);
        if (blockEndTime == null)
            return 0;

        long secondsRemaining = java.time.Duration.between(LocalDateTime.now(), blockEndTime).getSeconds();
        return Math.max(0L, secondsRemaining);
    }

    private String getClientKey(HttpServletRequest request) {
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

    // Cleanup method to prevent memory leaks (can be called periodically)
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes((long) blockDurationMinutes + windowMinutes);

        blockTimestamps.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        lastAttemptTimes.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        // Also clean up attempt counts for clients without recent activity
        lastAttemptTimes.keySet().forEach(key -> {
            if (!attemptCounts.containsKey(key)) {
                attemptCounts.remove(key);
            }
        });
    }
}
