package com.flexcodelabs.flextuma.core.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimiterService {

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

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

        if (checkRedisRateLimit(tenantId)) {
            return;
        }
        Bucket bucket = buckets.computeIfAbsent(tenantId, this::createNewBucket);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for tenant/user {}", tenantId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again later.");
        }
    }

    private boolean checkRedisRateLimit(UUID tenantId) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String key = "flextuma:rate:messages:" + tenantId + ":" + (System.currentTimeMillis() / 1000);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(2));
            }
            if (count != null && count > 10) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded. Please try again later.");
            }
            return true;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis rate limiter unavailable; using local fallback: {}", e.getMessage());
            return false;
        }
    }
}
