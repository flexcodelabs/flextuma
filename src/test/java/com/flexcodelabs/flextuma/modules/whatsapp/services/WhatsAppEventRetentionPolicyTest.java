package com.flexcodelabs.flextuma.modules.whatsapp.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppEventRetentionPolicyTest {

    private final WhatsAppEventRetentionPolicy policy = new WhatsAppEventRetentionPolicy(30, 90);

    @Test
    void effectiveDays_shouldUseDefault_whenNoOverrides() {
        assertEquals(30, policy.effectiveDays(null, null));
        assertEquals(30, policy.effectiveDays(0, 0));
    }

    @Test
    void effectiveDays_shouldPreferWebhookThenAccount() {
        assertEquals(7, policy.effectiveDays(7, 60));
        assertEquals(60, policy.effectiveDays(null, 60));
        assertEquals(60, policy.effectiveDays(0, 60));
    }

    @Test
    void effectiveDays_shouldCapEveryLevelAtMaximum() {
        assertEquals(90, policy.effectiveDays(400, null));
        assertEquals(90, policy.effectiveDays(null, 400));
    }

    @Test
    void constructor_shouldCapDefaultAtMaximum() {
        assertEquals(10, new WhatsAppEventRetentionPolicy(30, 10).defaultDays());
    }

    @Test
    void validateOverride_shouldAllowZeroToResetAndRejectOutOfRange() {
        assertDoesNotThrow(() -> policy.validateOverride(null));
        assertDoesNotThrow(() -> policy.validateOverride(0));
        assertDoesNotThrow(() -> policy.validateOverride(90));
        assertThrows(IllegalArgumentException.class, () -> policy.validateOverride(-1));
        assertThrows(IllegalArgumentException.class, () -> policy.validateOverride(91));
    }
}
