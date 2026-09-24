package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pulls Meta-approved WhatsApp Business templates into {@link WhatsAppTemplate} rows. Templates
 * are synced, never hand-authored -- see docs/whatsapp-templates.md "Template sync".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppTemplateSyncService {
    private static final String WHATSAPP_PROVIDER = "WHATSAPP";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\d+)}}");

    private final WhatsAppTemplateRepository repository;
    private final SmsConnectorRepository connectorRepository;
    private final CurrentUserResolver currentUserResolver;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** Syncs every active WHATSAPP connector the caller owns -- a tenant may have more than one
     * (e.g. multiple WABAs), so this isn't limited to a single connector. */
    @Transactional
    public List<WhatsAppTemplate> syncForCurrentUser() {
        User currentUser = currentUserResolver.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));

        List<SmsConnector> connectors = connectorRepository
                .findAllByCreatedByAndProviderAndActiveTrue(currentUser, WHATSAPP_PROVIDER);
        if (connectors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active WhatsApp connector found");
        }

        List<WhatsAppTemplate> synced = new ArrayList<>();
        for (SmsConnector connector : connectors) {
            synced.addAll(sync(connector));
        }
        return synced;
    }

    @Transactional
    public List<WhatsAppTemplate> sync(SmsConnector connector) {
        String businessAccountId = extractBusinessAccountId(connector);
        if (businessAccountId == null || businessAccountId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Connector [" + connector.getId()
                            + "] has no businessAccountId configured in extraSettings. Edit the connector and set"
                            + " Extra settings to {\"businessAccountId\": \"<your WhatsApp Business Account ID>\"}");
        }

        List<Map<String, Object>> fetched = fetchAllTemplates(connector, businessAccountId);

        Map<String, WhatsAppTemplate> existingByMetaId = repository
                .findByConnectorAndCreatedBy(connector, connector.getCreatedBy()).stream()
                .collect(Collectors.toMap(WhatsAppTemplate::getMetaTemplateId, t -> t, (a, b) -> a));

        Set<String> seenMetaIds = new HashSet<>();
        List<WhatsAppTemplate> upserted = new ArrayList<>();
        for (Map<String, Object> raw : fetched) {
            Object idObj = raw.get("id");
            if (idObj == null) {
                continue;
            }
            String metaTemplateId = idObj.toString();
            seenMetaIds.add(metaTemplateId);
            WhatsAppTemplate template = existingByMetaId.getOrDefault(metaTemplateId, new WhatsAppTemplate());
            applyMetaTemplate(template, raw, connector);
            upserted.add(repository.save(template));
        }

        // A template Meta no longer returns is marked REMOVED rather than deleted, so any
        // existing send configuration referencing it fails validation instead of dangling.
        for (WhatsAppTemplate existing : existingByMetaId.values()) {
            if (!seenMetaIds.contains(existing.getMetaTemplateId())
                    && !WhatsAppTemplate.STATUS_REMOVED.equals(existing.getStatus())) {
                existing.setStatus(WhatsAppTemplate.STATUS_REMOVED);
                existing.setLastSyncedAt(LocalDateTime.now());
                repository.save(existing);
            }
        }

        log.info("Synced {} WhatsApp template(s) for connector [{}]", upserted.size(), connector.getId());
        return upserted;
    }

    @SuppressWarnings("unchecked")
    private String extractBusinessAccountId(SmsConnector connector) {
        String extraSettings = connector.getExtraSettings();
        if (extraSettings == null || extraSettings.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> settings = objectMapper.readValue(extraSettings, Map.class);
            Object value = settings.get("businessAccountId");
            return value == null ? null : value.toString();
        } catch (Exception e) {
            log.warn("Connector [{}] has unparsable extraSettings: {}", connector.getId(), e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> fetchAllTemplates(SmsConnector connector, String businessAccountId) {
        List<Map<String, Object>> all = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(connector.getKey());

        String url = connector.getUrl().replaceAll("/+$", "") + "/" + businessAccountId
                + "/message_templates?limit=100";
        int guard = 0;
        while (url != null && guard++ < 100) {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = objectMapper.convertValue(response.getBody(), new TypeReference<>() {
            });
            all.addAll(extractData(body));
            url = nextPageUrl(body);
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractData(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                data.add((Map<String, Object>) map);
            }
        }
        return data;
    }

    private String nextPageUrl(Map<String, Object> body) {
        if (body == null || !(body.get("paging") instanceof Map<?, ?> paging)) {
            return null;
        }
        Object next = paging.get("next");
        return next == null ? null : next.toString();
    }

    private void applyMetaTemplate(WhatsAppTemplate template, Map<String, Object> raw, SmsConnector connector) {
        template.setMetaTemplateId(stringOrNull(raw.get("id")));
        template.setName(stringOrNull(raw.get("name")));
        template.setCategory(stringOrNull(raw.get("category")));
        template.setLanguage(stringOrNull(raw.get("language")));
        template.setStatus(stringOrNull(raw.get("status")));
        template.setConnector(connector);
        if (template.getCreatedBy() == null) {
            template.setCreatedBy(connector.getCreatedBy());
        }

        Object components = raw.get("components");
        try {
            template.setComponentsJson(objectMapper.writeValueAsString(components != null ? components : List.of()));
            template.setPlaceholdersJson(objectMapper.writeValueAsString(derivePlaceholders(components)));
        } catch (Exception e) {
            log.warn("Failed to serialize components for template [{}]: {}", template.getMetaTemplateId(),
                    e.getMessage());
        }
        template.setLastSyncedAt(LocalDateTime.now());
    }

    /** Derives a flat {component, type, position} placeholder list from each component's
     * {{n}} count/format: text placeholders from HEADER/BODY text, a single media placeholder
     * for a non-TEXT header format, and one per dynamic-URL button. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> derivePlaceholders(Object componentsObj) {
        List<Map<String, Object>> placeholders = new ArrayList<>();
        if (!(componentsObj instanceof List<?> components)) {
            return placeholders;
        }
        for (Object c : components) {
            if (!(c instanceof Map<?, ?> component)) {
                continue;
            }
            String type = stringOrNull(component.get("type"));
            if (type == null) {
                continue;
            }
            if ("BUTTONS".equalsIgnoreCase(type)) {
                placeholders.addAll(buttonPlaceholders((Map<String, Object>) component));
                continue;
            }
            String format = stringOrNull(component.get("format"));
            if (format != null && !"TEXT".equalsIgnoreCase(format)) {
                placeholders.add(Map.of("component", type, "type", format.toLowerCase(), "position", 1));
                continue;
            }
            String text = stringOrNull(component.get("text"));
            if (text == null) {
                continue;
            }
            for (Integer position : placeholderPositions(text)) {
                placeholders.add(Map.of("component", type, "type", "text", "position", position));
            }
        }
        return placeholders;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buttonPlaceholders(Map<String, Object> buttonsComponent) {
        List<Map<String, Object>> placeholders = new ArrayList<>();
        if (!(buttonsComponent.get("buttons") instanceof List<?> buttons)) {
            return placeholders;
        }
        for (int i = 0; i < buttons.size(); i++) {
            if (!(buttons.get(i) instanceof Map<?, ?> button)) {
                continue;
            }
            String buttonType = stringOrNull(button.get("type"));
            if ("URL".equalsIgnoreCase(buttonType) && button.get("example") != null) {
                placeholders.add(Map.of("component", "BUTTON", "subType", "url", "index", i, "type", "text"));
            }
        }
        return placeholders;
    }

    private Set<Integer> placeholderPositions(String text) {
        Set<Integer> positions = new TreeSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            positions.add(Integer.parseInt(matcher.group(1)));
        }
        return positions;
    }

    private String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
