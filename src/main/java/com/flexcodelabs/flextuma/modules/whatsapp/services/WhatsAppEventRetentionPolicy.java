package com.flexcodelabs.flextuma.modules.whatsapp.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Data retention policy for the WhatsApp webhook event log. The platform sets a default and a
 * maximum; an account (DataRetentionPolicy) may override the default, and a single webhook config
 * may override its account via eventRetentionDays. Precedence: webhook, then account, then platform
 * default, always capped at the maximum. Null or 0 at any level means "inherit".
 */
@Component
public class WhatsAppEventRetentionPolicy {
    private final int defaultDays;
    private final int maxDays;

    public WhatsAppEventRetentionPolicy(
            @Value("${flextuma.whatsapp.events.retention-days:30}") int defaultDays,
            @Value("${flextuma.whatsapp.events.max-retention-days:365}") int maxDays) {
        this.maxDays = Math.max(1, maxDays);
        this.defaultDays = Math.min(Math.max(1, defaultDays), this.maxDays);
    }

    public int defaultDays() {
        return defaultDays;
    }

    public int maxDays() {
        return maxDays;
    }

    /** Days actually applied given a webhook override and an account override (either may be null). */
    public int effectiveDays(Integer webhookDays, Integer accountDays) {
        if (webhookDays != null && webhookDays > 0) return Math.min(webhookDays, maxDays);
        if (accountDays != null && accountDays > 0) return Math.min(accountDays, maxDays);
        return defaultDays;
    }

    /** Rejects an override outside 0..maxDays (0 resets it to inherit). */
    public void validateOverride(Integer override) {
        if (override != null && (override < 0 || override > maxDays)) {
            throw new IllegalArgumentException("eventRetentionDays must be between 1 and " + maxDays
                    + " days (or 0 to inherit the default)");
        }
    }
}
