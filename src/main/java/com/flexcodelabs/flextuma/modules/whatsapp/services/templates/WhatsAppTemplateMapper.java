package com.flexcodelabs.flextuma.modules.whatsapp.services.templates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** Copies one template from Meta's message_templates response onto a {@link WhatsAppTemplate}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppTemplateMapper {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\d+)}}");
    private static final String COMPONENT = "component";
    private static final String TYPE = "type";
    private static final String TEXT = "text";

    private final ObjectMapper objectMapper;

    public void apply(WhatsAppTemplate template, Map<String, Object> raw, SmsConnector connector, String businessAccountId) {
        template.setMetaTemplateId(stringOrNull(raw.get("id")));
        template.setName(stringOrNull(raw.get("name")));
        template.setCategory(stringOrNull(raw.get("category")));
        template.setLanguage(stringOrNull(raw.get("language")));
        template.setStatus(stringOrNull(raw.get("status")));
        template.setConnector(connector);
        template.setBusinessAccountId(businessAccountId);
        if (template.getCreatedBy() == null) {
            template.setCreatedBy(connector.getCreatedBy());
        }
        List<Map<String, Object>> components = WhatsAppPayloads.mapList(raw, "components");
        try {
            template.setComponentsJson(objectMapper.writeValueAsString(components));
            template.setPlaceholdersJson(objectMapper.writeValueAsString(derivePlaceholders(components)));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize components for template [{}]: {}", template.getMetaTemplateId(), e.getMessage());
        }
        template.setLastSyncedAt(LocalDateTime.now());
    }

    /** A flat {component, type, position} list: text placeholders from HEADER/BODY text, one media
     * placeholder for a non-TEXT header format, and one per dynamic-URL button. */
    List<Map<String, Object>> derivePlaceholders(List<Map<String, Object>> components) {
        List<Map<String, Object>> placeholders = new ArrayList<>();
        components.forEach(component -> placeholders.addAll(placeholdersOf(component)));
        return placeholders;
    }

    private List<Map<String, Object>> placeholdersOf(Map<String, Object> component) {
        String type = stringOrNull(component.get(TYPE));
        if (type == null) {
            return List.of();
        }
        if ("BUTTONS".equalsIgnoreCase(type)) {
            return buttonPlaceholders(component);
        }
        String format = stringOrNull(component.get("format"));
        if (format != null && !"TEXT".equalsIgnoreCase(format)) {
            return List.of(Map.of(COMPONENT, type, TYPE, format.toLowerCase(), "position", 1));
        }
        String text = stringOrNull(component.get(TEXT));
        return text == null ? List.of() : placeholderPositions(text).stream()
                .map(position -> Map.<String, Object>of(COMPONENT, type, TYPE, TEXT, "position", position)).toList();
    }

    private List<Map<String, Object>> buttonPlaceholders(Map<String, Object> buttonsComponent) {
        List<Map<String, Object>> buttons = WhatsAppPayloads.mapList(buttonsComponent, "buttons");
        return IntStream.range(0, buttons.size())
                .filter(i -> "URL".equalsIgnoreCase(stringOrNull(buttons.get(i).get(TYPE))) && buttons.get(i).get("example") != null)
                .mapToObj(i -> Map.<String, Object>of(COMPONENT, "BUTTON", "subType", "url", "index", i, TYPE, TEXT))
                .toList();
    }

    private Set<Integer> placeholderPositions(String text) {
        Set<Integer> positions = new TreeSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            positions.add(Integer.parseInt(matcher.group(1)));
        }
        return positions;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
