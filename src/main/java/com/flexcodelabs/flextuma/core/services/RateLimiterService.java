package com.flexcodelabs.flextuma.core.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimiterService {

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createNewBucket(UUID tenantId) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    public void checkRateLimit(UUID tenantId) {
        if (tenantId == null) {
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(tenantId, this::createNewBucket);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for tenant/user {}", tenantId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again later.");
        }
    }
}
