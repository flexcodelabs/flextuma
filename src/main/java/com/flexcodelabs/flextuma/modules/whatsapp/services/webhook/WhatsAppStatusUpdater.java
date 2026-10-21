package com.flexcodelabs.flextuma.modules.whatsapp.services.webhook;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.STATUS;
import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.STATUSES;
import static com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads.TEMPLATE_STATUS_FIELD;

/** Applies Meta message-status callbacks to SmsLogs and template review events to WhatsAppTemplates. */
@Component
@RequiredArgsConstructor
public class WhatsAppStatusUpdater {
    private static final Map<String, SmsLogStatus> STATUS_BY_META_VALUE = Map.of(
            "read", SmsLogStatus.READ,
            "delivered", SmsLogStatus.DELIVERED,
            "failed", SmsLogStatus.FAILED,
            "sent", SmsLogStatus.SENT);

    // sent < delivered < read: Meta's status callbacks can arrive out of order (or duplicated),
    // and without this a late "delivered" retry could visibly regress an already-READ message's
    // blue ticks back to gray. FAILED isn't in this progression -- it's a terminal outcome, not
    // a step past it.
    private static final List<SmsLogStatus> DELIVERY_PROGRESSION = List.of(SmsLogStatus.SENT, SmsLogStatus.DELIVERED, SmsLogStatus.READ);

    private final SmsLogRepository smsLogRepository;
    private final WhatsAppTemplateRepository templateRepository;

    public void updateDeliveryStatus(Map<String, Object> payload) {
        WhatsAppPayloads.changes(payload).stream()
                .flatMap(change -> WhatsAppPayloads.mapList(WhatsAppPayloads.value(change), STATUSES).stream())
                .forEach(this::applyStatusUpdate);
    }

    private void applyStatusUpdate(Map<String, Object> raw) {
        Object id = raw.get("id");
        SmsLogStatus next = STATUS_BY_META_VALUE.get(String.valueOf(raw.get(STATUS)).toLowerCase(Locale.ROOT));
        if (id == null || next == null) {
            return;
        }
        smsLogRepository.findByProviderMessageId(id.toString()).ifPresent(logEntry -> applyStatus(logEntry, next, raw));
    }

    private void applyStatus(SmsLog logEntry, SmsLogStatus next, Map<String, Object> raw) {
        if (next != SmsLogStatus.FAILED && isDeliveryRegression(logEntry.getStatus(), next)) {
            return;
        }
        logEntry.setStatus(next);
        if (next == SmsLogStatus.FAILED) {
            logEntry.setError(WhatsAppPayloads.extractErrorMessage(raw));
        }
        smsLogRepository.save(logEntry);
    }

    private boolean isDeliveryRegression(SmsLogStatus current, SmsLogStatus next) {
        // List.of(...)'s indexOf() throws NPE on a null argument, and current is null for a log
        // whose status hasn't been set yet, so this must short-circuit first.
        if (current == null) {
            return false;
        }
        int currentIndex = DELIVERY_PROGRESSION.indexOf(current);
        int nextIndex = DELIVERY_PROGRESSION.indexOf(next);
        return currentIndex >= 0 && nextIndex >= 0 && nextIndex < currentIndex;
    }

    /** Updates the matching local WhatsAppTemplate.status (by metaTemplateId, falling back to
     * name+language) so an approval/rejection shows up immediately instead of waiting for the next
     * manual sync. Both lookups are scoped to config's owner -- metaTemplateId and name+language
     * are each only unique per (value, creator), so an unscoped lookup could corrupt a different
     * tenant's template of the same id or common name (e.g. "otp_verification"). */
    public void updateTemplateStatus(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        WhatsAppPayloads.changes(payload).stream()
                .filter(change -> TEMPLATE_STATUS_FIELD.equals(change.get("field")))
                .map(WhatsAppPayloads::value)
                .filter(value -> value.get("event") != null)
                .forEach(value -> findTemplate(config, value).ifPresent(template -> saveTemplateStatus(template, value)));
    }

    private Optional<WhatsAppTemplate> findTemplate(WhatsAppWebhookConfig config, Map<String, Object> value) {
        Optional<WhatsAppTemplate> byId = WhatsAppPayloads.text(value.get("message_template_id"))
                .flatMap(id -> templateRepository.findFirstByMetaTemplateIdAndCreatedBy(id, config.getCreatedBy()));
        return byId.isPresent() ? byId : findTemplateByNameAndLanguage(config, value);
    }

    private Optional<WhatsAppTemplate> findTemplateByNameAndLanguage(WhatsAppWebhookConfig config, Map<String, Object> value) {
        Object name = value.get("message_template_name");
        Object language = value.get("message_template_language");
        if (name == null || language == null) {
            return Optional.empty();
        }
        return templateRepository.findFirstByNameAndLanguageAndCreatedBy(name.toString(), language.toString(), config.getCreatedBy());
    }

    private void saveTemplateStatus(WhatsAppTemplate template, Map<String, Object> value) {
        template.setStatus(value.get("event").toString());
        template.setLastSyncedAt(LocalDateTime.now());
        templateRepository.save(template);
    }
}
