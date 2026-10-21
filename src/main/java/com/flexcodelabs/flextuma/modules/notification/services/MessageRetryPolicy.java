package com.flexcodelabs.flextuma.modules.notification.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Retry semantics modelled on Kafka retry topics: each failed attempt is re-queued with an
 * exponentially growing delay (initial × multiplier^(attempt-1), capped at max-interval). Every
 * error is retried -- a 4xx today may succeed once the receiving side fixes its config -- until
 * the retry window (12h by default, measured from the first failure) runs out, after which the
 * message is dead-lettered as FAILED.
 */
@Slf4j
@Component
public class MessageRetryPolicy {

    private final long initialIntervalMs;
    private final double multiplier;
    private final long maxIntervalMs;
    private final Duration retryWindow;

    public MessageRetryPolicy(
            @Value("${flextuma.messaging.retry.initial-interval-ms:10000}") long initialIntervalMs,
            @Value("${flextuma.messaging.retry.multiplier:2.0}") double multiplier,
            @Value("${flextuma.messaging.retry.max-interval-ms:3600000}") long maxIntervalMs,
            @Value("${flextuma.messaging.retry.window-ms:43200000}") long retryWindowMs) {
        this.initialIntervalMs = Math.max(0, initialIntervalMs);
        this.multiplier = Math.max(1.0, multiplier);
        this.maxIntervalMs = Math.max(this.initialIntervalMs, maxIntervalMs);
        this.retryWindow = Duration.ofMillis(Math.max(0, retryWindowMs));
    }

    /** Delay before the retry that follows the given (1-based) failed attempt. */
    public Duration backoff(int failedAttempt) {
        double delay = initialIntervalMs * Math.pow(multiplier, Math.max(0, failedAttempt - 1));
        return Duration.ofMillis((long) Math.min(delay, maxIntervalMs));
    }

    /** Delay before the next retry, or empty once the window opened at {@code windowStart} has run out. */
    public Optional<Duration> nextRetryDelay(int failedAttempt, LocalDateTime windowStart) {
        if (windowStart != null && !LocalDateTime.now().isBefore(windowStart.plus(retryWindow))) {
            return Optional.empty();
        }
        return Optional.of(backoff(failedAttempt));
    }

    /** Records a failed attempt on the log: schedules the next retry, or dead-letters it as FAILED. */
    public void onFailure(SmsLog smsLog, SmsSendResult result) {
        int attempt = smsLog.getDeliveryAttempts();
        smsLog.setError(result.getMessage());
        smsLog.setProviderResponse(result.getProviderResponse());
        if (smsLog.getRetryWindowStartedAt() == null) {
            smsLog.setRetryWindowStartedAt(LocalDateTime.now());
        }

        Optional<Duration> delay = nextRetryDelay(attempt, smsLog.getRetryWindowStartedAt());
        if (delay.isPresent()) {
            smsLog.setStatus(SmsLogStatus.PENDING);
            smsLog.setRetries(smsLog.getRetries() + 1);
            smsLog.setNextRetryAt(LocalDateTime.now().plus(delay.get()));
            log.warn("SmsLog [{}] attempt {} failed, retrying in {}s: {}", smsLog.getId(), attempt,
                    delay.get().toSeconds(), result.getMessage());
            return;
        }

        deadLetter(smsLog, result.getMessage());
        log.warn("SmsLog [{}] FAILED after {} attempt(s) over the {}h retry window: {}", smsLog.getId(), attempt,
                retryWindow.toHours(), result.getMessage());
    }

    /** Marks the log FAILED without scheduling another attempt. */
    public void deadLetter(SmsLog smsLog, String error) {
        smsLog.setStatus(SmsLogStatus.FAILED);
        smsLog.setError(error);
        smsLog.setNextRetryAt(null);
    }
}
