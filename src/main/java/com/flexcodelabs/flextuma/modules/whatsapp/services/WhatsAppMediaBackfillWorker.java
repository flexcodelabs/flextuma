package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppInboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Retries downloading WhatsApp inbound media that failed to cache at webhook ingestion time
 * (e.g. a briefly unusable connector token), so a message doesn't stay permanently unavailable
 * just because nobody opened the conversation to trigger {@link WhatsAppInboxMessageService}'s
 * on-read retry. Bounded to recently received messages -- Meta's CDN only keeps media
 * retrievable for a limited window, so retrying older failures indefinitely would just waste
 * batch slots on media that's gone for good.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMediaBackfillWorker {

    private final WhatsAppInboxMessageRepository inboxMessageRepository;
    private final WhatsAppMediaService mediaService;

    @Value("${flextuma.whatsapp.media-backfill.batch-size:25}")
    private int batchSize;

    @Value("${flextuma.whatsapp.media-backfill.max-age-days:3}")
    private int maxAgeDays;

    @Scheduled(fixedDelayString = "${flextuma.whatsapp.media-backfill.interval-ms:300000}")
    @Transactional
    public void backfillMissingMedia() {
        List<WhatsAppInboxMessage> candidates = inboxMessageRepository
                .findByMediaIdIsNotNullAndMediaPathIsNullAndReceivedAtAfter(
                        LocalDateTime.now().minusDays(maxAgeDays), PageRequest.of(0, batchSize));

        if (candidates.isEmpty()) {
            return;
        }

        log.info("WhatsAppMediaBackfillWorker: Retrying {} message(s) with missing media", candidates.size());

        for (WhatsAppInboxMessage message : candidates) {
            backfillOne(message);
        }
    }

    private void backfillOne(WhatsAppInboxMessage message) {
        try {
            mediaService.download(message.getConfig(), message.getMediaId()).ifPresentOrElse(downloaded -> {
                message.setMediaPath(downloaded.path());
                message.setMediaSize(downloaded.size());
                inboxMessageRepository.save(message);
                log.info("WhatsAppMediaBackfillWorker: Recovered media for message [{}]", message.getId());
            }, () -> log.debug("WhatsAppMediaBackfillWorker: Media for message [{}] still unavailable", message.getId()));
        } catch (Exception e) {
            log.error("WhatsAppMediaBackfillWorker: Error backfilling media for message [{}]: {}",
                    message.getId(), e.getMessage());
        }
    }
}
