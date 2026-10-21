package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookEventRepository;
import com.flexcodelabs.flextuma.modules.retention.services.DataRetentionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Nightly purge of the WhatsApp event log according to WhatsAppEventRetentionPolicy. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppEventRetentionScheduler {
    private final WhatsAppWebhookEventRepository eventRepository;
    private final WhatsAppWebhookConfigRepository configRepository;
    private final WhatsAppEventRetentionPolicy policy;
    private final DataRetentionPolicyService accountPolicies;

    /** One delete per webhook config -- there's one config per WhatsApp phone number, so this stays small. */
    @Scheduled(cron = "${flextuma.whatsapp.events.purge-cron:0 30 2 * * *}")
    @Transactional
    public void purgeExpiredEvents() {
        LocalDateTime now = LocalDateTime.now();
        Map<UUID, Optional<Integer>> accountDaysByOwner = new HashMap<>();
        int deleted = 0;
        for (WhatsAppWebhookConfig config : configRepository.findAll()) {
            User owner = config.getCreatedBy();
            Integer accountDays = owner == null ? null : accountDaysByOwner
                    .computeIfAbsent(owner.getId(), id -> Optional.ofNullable(accountPolicies.whatsAppEventDaysFor(owner)))
                    .orElse(null);
            int days = policy.effectiveDays(config.getEventRetentionDays(), accountDays);
            deleted += eventRepository.deleteByConfigIdAndReceivedAtBefore(config.getId(), now.minusDays(days));
        }
        if (deleted > 0) {
            log.info("Purged {} WhatsApp webhook events past their retention period", deleted);
        }
    }
}
