package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.helpers.WhatsAppConnectorSettings;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppTemplateSyncResultDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppTemplateSyncResultDTO.Account;
import com.flexcodelabs.flextuma.modules.whatsapp.services.meta.MetaGraphException;
import com.flexcodelabs.flextuma.modules.whatsapp.services.templates.WhatsAppTemplateFetcher;
import com.flexcodelabs.flextuma.modules.whatsapp.services.templates.WhatsAppTemplateMapper;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pulls Meta-approved WhatsApp Business templates into {@link WhatsAppTemplate} rows. Templates
 * belong to a WhatsApp Business Account (WABA), not to one phone number, so each account is synced
 * once however many numbers it has, and any of its numbers can then send them. Templates are
 * synced, never hand-authored -- see docs/whatsapp-templates.md "Template sync".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppTemplateSyncService {
    private static final String WHATSAPP_PROVIDER = "WHATSAPP";
    /** Grouping key for numbers with no WABA id; such numbers can't be synced. */
    private static final String NO_ACCOUNT = "";

    private final WhatsAppTemplateRepository repository;
    private final SmsConnectorRepository connectorRepository;
    private final CurrentUserResolver currentUserResolver;
    private final WhatsAppTemplateFetcher fetcher;
    private final WhatsAppTemplateMapper mapper;

    private record Fetched(SmsConnector connector, List<Map<String, Object>> templates) {
    }

    /** Syncs every account the caller has an active WhatsApp number in, or only {@code businessAccountId}. */
    @Transactional
    public WhatsAppTemplateSyncResultDTO syncForCurrentUser(String businessAccountId) {
        User currentUser = currentUserResolver.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        List<SmsConnector> connectors = connectorRepository.findAllByCreatedByAndProviderAndActiveTrue(currentUser, WHATSAPP_PROVIDER);
        if (connectors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active WhatsApp connector found");
        }
        Map<String, WhatsAppTemplate> existing = repository.findByCreatedBy(currentUser).stream()
                .collect(Collectors.toMap(WhatsAppTemplate::getMetaTemplateId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<Account> accounts = byAccount(connectors).entrySet().stream()
                .filter(account -> businessAccountId == null || account.getKey().equals(businessAccountId))
                .map(account -> syncAccount(account.getKey(), account.getValue(), existing))
                .toList();
        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active WhatsApp number in account " + businessAccountId);
        }
        return new WhatsAppTemplateSyncResultDTO(accounts.stream().mapToInt(Account::synced).sum(),
                accounts.stream().mapToInt(Account::removed).sum(), accounts);
    }

    private static Map<String, List<SmsConnector>> byAccount(List<SmsConnector> connectors) {
        return connectors.stream().collect(Collectors.groupingBy(
                connector -> Objects.requireNonNullElse(WhatsAppConnectorSettings.businessAccountIdOf(connector), NO_ACCOUNT),
                LinkedHashMap::new, Collectors.toList()));
    }

    private Account syncAccount(String businessAccountId, List<SmsConnector> connectors, Map<String, WhatsAppTemplate> existing) {
        List<String> phoneNumberIds = connectors.stream().map(SmsConnector::getSenderId).toList();
        if (NO_ACCOUNT.equals(businessAccountId)) {
            return new Account(null, phoneNumberIds, 0, 0,
                    "Add the WhatsApp Business Account ID to these numbers to sync their templates.");
        }
        try {
            Fetched fetched = fetchWithAnyNumber(businessAccountId, connectors);
            Set<String> seen = upsert(fetched, businessAccountId, existing);
            int removed = markRemoved(businessAccountId, connectors, existing, seen);
            log.info("Synced {} WhatsApp template(s) for account [{}]", seen.size(), businessAccountId);
            return new Account(businessAccountId, phoneNumberIds, seen.size(), removed, null);
        } catch (MetaGraphException e) {
            return new Account(businessAccountId, phoneNumberIds, 0, 0, e.getMessage());
        }
    }

    /** Numbers in one account usually share a token, but if one number's token was revoked another's may still work. */
    private Fetched fetchWithAnyNumber(String businessAccountId, List<SmsConnector> connectors) {
        MetaGraphException last = null;
        for (SmsConnector connector : connectors) {
            try {
                return new Fetched(connector, fetcher.fetchAll(connector, businessAccountId));
            } catch (MetaGraphException e) {
                last = e;
            }
        }
        throw Objects.requireNonNull(last);
    }

    private Set<String> upsert(Fetched fetched, String businessAccountId, Map<String, WhatsAppTemplate> existing) {
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> raw : fetched.templates()) {
            String metaTemplateId = WhatsAppPayloads.text(raw.get("id")).orElse(null);
            if (metaTemplateId == null) {
                continue;
            }
            seen.add(metaTemplateId);
            WhatsAppTemplate template = existing.getOrDefault(metaTemplateId, new WhatsAppTemplate());
            mapper.apply(template, raw, fetched.connector(), businessAccountId);
            existing.put(metaTemplateId, repository.save(template));
        }
        return seen;
    }

    /** A template Meta no longer returns is marked REMOVED rather than deleted, so any send
     * configuration referencing it fails validation instead of dangling. */
    private int markRemoved(String businessAccountId, List<SmsConnector> connectors, Map<String, WhatsAppTemplate> existing, Set<String> seen) {
        Set<UUID> connectorIds = connectors.stream().map(SmsConnector::getId).collect(Collectors.toSet());
        List<WhatsAppTemplate> gone = existing.values().stream()
                .filter(template -> belongsTo(template, businessAccountId, connectorIds))
                .filter(template -> !seen.contains(template.getMetaTemplateId()))
                .filter(template -> !WhatsAppTemplate.STATUS_REMOVED.equals(template.getStatus()))
                .toList();
        gone.forEach(template -> {
            template.setStatus(WhatsAppTemplate.STATUS_REMOVED);
            template.setLastSyncedAt(LocalDateTime.now());
            repository.save(template);
        });
        return gone.size();
    }

    /** Rows synced before business_account_id existed are matched by the connector they came through. */
    private static boolean belongsTo(WhatsAppTemplate template, String businessAccountId, Set<UUID> connectorIds) {
        if (template.getBusinessAccountId() != null) {
            return template.getBusinessAccountId().equals(businessAccountId);
        }
        return template.getConnector() != null && connectorIds.contains(template.getConnector().getId());
    }
}
