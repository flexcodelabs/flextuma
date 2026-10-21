package com.flexcodelabs.flextuma.modules.whatsapp.services.webhook;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Splits one delivery on a Meta app's shared callback URL into per-config deliveries.
 *
 * <p>Meta sends every WABA and phone number subscribed to an app to the same URL, batching
 * changes from several numbers into one POST. Each change is routed by
 * {@code value.metadata.phone_number_id}; WABA-level changes that carry no phone number
 * (template status, account updates) go to every config of that WABA ({@code entry[].id}), or to
 * the app's only config when there is just one. Only configs linked to this app are candidates,
 * so a shared URL can never reach another app's (or tenant's) configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppSharedWebhookRouter {
    private final WhatsAppWebhookConfigRepository configRepository;

    /** A config together with the subset of the delivery addressed to it. */
    public record RoutedDelivery(WhatsAppWebhookConfig config, Map<String, Object> payload) {
    }

    public List<RoutedDelivery> route(WhatsAppMetaApp app, Map<String, Object> payload) {
        Candidates candidates = new Candidates(configRepository.findAllActiveByMetaAppId(app.getId()));
        Map<UUID, List<Map<String, Object>>> entriesByConfig = new LinkedHashMap<>();
        for (Map<String, Object> entry : WhatsAppPayloads.entries(payload)) {
            String wabaId = WhatsAppPayloads.text(entry.get("id")).orElse(null);
            for (Map<String, Object> change : WhatsAppPayloads.mapList(entry, "changes")) {
                candidates.targetsOf(change, wabaId).forEach(config -> entriesByConfig
                        .computeIfAbsent(config.getId(), id -> new ArrayList<>()).add(singleChangeEntry(wabaId, change)));
            }
        }
        return entriesByConfig.entrySet().stream()
                .map(routed -> new RoutedDelivery(candidates.byId(routed.getKey()), payloadOf(payload, routed.getValue())))
                .toList();
    }

    private static Map<String, Object> singleChangeEntry(String wabaId, Map<String, Object> change) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", wabaId);
        entry.put("changes", List.of(change));
        return entry;
    }

    private static Map<String, Object> payloadOf(Map<String, Object> original, List<Map<String, Object>> entries) {
        Map<String, Object> routed = new LinkedHashMap<>();
        routed.put("object", original.getOrDefault("object", "whatsapp_business_account"));
        routed.put("entry", entries);
        return routed;
    }

    private static final class Candidates {
        private final List<WhatsAppWebhookConfig> configs;
        private final Map<String, WhatsAppWebhookConfig> byPhoneNumberId;
        private final Map<UUID, WhatsAppWebhookConfig> byConfigId;

        Candidates(List<WhatsAppWebhookConfig> configs) {
            this.configs = configs;
            this.byPhoneNumberId = configs.stream().collect(
                    Collectors.toMap(WhatsAppWebhookConfig::getPhoneNumberId, Function.identity(), (first, second) -> first));
            this.byConfigId = configs.stream().collect(Collectors.toMap(WhatsAppWebhookConfig::getId, Function.identity()));
        }

        WhatsAppWebhookConfig byId(UUID id) {
            return byConfigId.get(id);
        }

        List<WhatsAppWebhookConfig> targetsOf(Map<String, Object> change, String wabaId) {
            Optional<String> phoneNumberId = WhatsAppPayloads.phoneNumberId(change);
            if (phoneNumberId.isPresent()) {
                return byPhoneNumber(phoneNumberId.get());
            }
            return byBusinessAccount(wabaId);
        }

        private List<WhatsAppWebhookConfig> byPhoneNumber(String phoneNumberId) {
            WhatsAppWebhookConfig config = byPhoneNumberId.get(phoneNumberId);
            if (config == null) {
                log.warn("Ignoring shared WhatsApp webhook change for unregistered phone number [{}]", phoneNumberId);
                return List.of();
            }
            return List.of(config);
        }

        private List<WhatsAppWebhookConfig> byBusinessAccount(String wabaId) {
            List<WhatsAppWebhookConfig> matches = configs.stream()
                    .filter(config -> wabaId != null && Objects.equals(wabaId, config.getBusinessAccountId()))
                    .toList();
            if (matches.isEmpty() && configs.size() == 1) {
                return configs;
            }
            if (matches.isEmpty()) {
                log.warn("Ignoring shared WhatsApp webhook change for WABA [{}]: set businessAccountId on its configuration", wabaId);
            }
            return matches;
        }
    }
}
