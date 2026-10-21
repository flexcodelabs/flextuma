package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookEventRepository;
import com.flexcodelabs.flextuma.modules.retention.services.DataRetentionPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppEventRetentionSchedulerTest {

    @Mock
    private WhatsAppWebhookEventRepository eventRepository;

    @Mock
    private WhatsAppWebhookConfigRepository configRepository;

    @Mock
    private DataRetentionPolicyService accountPolicies;

    @Test
    void purgeExpiredEvents_shouldApplyWebhookThenAccountThenDefault_cappedAtMaximum() {
        User accountWithPolicy = user();
        User accountWithout = user();
        WhatsAppWebhookConfig webhookOverride = config(accountWithPolicy, 7);
        WhatsAppWebhookConfig inheritsAccount = config(accountWithPolicy, null);
        WhatsAppWebhookConfig inheritsDefault = config(accountWithout, 0);
        WhatsAppWebhookConfig overCap = config(accountWithout, 400);
        when(configRepository.findAll()).thenReturn(List.of(webhookOverride, inheritsAccount, inheritsDefault, overCap));
        when(accountPolicies.whatsAppEventDaysFor(accountWithPolicy)).thenReturn(60);
        when(accountPolicies.whatsAppEventDaysFor(accountWithout)).thenReturn(null);

        LocalDateTime now = LocalDateTime.now();
        new WhatsAppEventRetentionScheduler(eventRepository, configRepository,
                new WhatsAppEventRetentionPolicy(30, 90), accountPolicies).purgeExpiredEvents();

        assertCutoff(webhookOverride, now.minusDays(7));
        assertCutoff(inheritsAccount, now.minusDays(60));
        assertCutoff(inheritsDefault, now.minusDays(30));
        assertCutoff(overCap, now.minusDays(90));
        // Account policy is looked up once per owner, not once per config.
        verify(accountPolicies, times(1)).whatsAppEventDaysFor(accountWithPolicy);
    }

    private void assertCutoff(WhatsAppWebhookConfig config, LocalDateTime expected) {
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(eventRepository).deleteByConfigIdAndReceivedAtBefore(eq(config.getId()), cutoff.capture());
        assertTrue(Duration.between(expected, cutoff.getValue()).abs().toSeconds() < 5,
                "expected ~" + expected + " but was " + cutoff.getValue());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private WhatsAppWebhookConfig config(User owner, Integer retentionDays) {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setCreatedBy(owner);
        config.setEventRetentionDays(retentionDays);
        return config;
    }
}
