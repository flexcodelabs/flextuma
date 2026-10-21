package com.flexcodelabs.flextuma.modules.whatsapp.services.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppMetaCheckRequest;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppMetaCheckResult;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppMetaPhoneNumberDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Checks WhatsApp Cloud API credentials against Meta and reads phone numbers from it. */
@Service
@RequiredArgsConstructor
public class WhatsAppMetaService {
    static final String DEFAULT_GRAPH_API_URL = "https://graph.facebook.com/v21.0";
    private static final String PHONE_FIELDS = "id,display_phone_number,verified_name,quality_rating,code_verification_status,name_status,platform_type";
    private static final Pattern META_ID = Pattern.compile("^\\d{5,32}$");
    private static final String DATA = "data";

    private final MetaGraphClient graph;
    private final SmsConnectorRepository connectorRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ObjectMapper objectMapper;

    record Credentials(String baseUrl, String token, String businessAccountId, String phoneNumberId) {
    }

    public WhatsAppMetaCheckResult check(WhatsAppMetaCheckRequest request) {
        try {
            return check(resolve(request));
        } catch (MetaGraphException e) {
            return WhatsAppMetaCheckResult.failure(e.getCode(), e.getMessage());
        }
    }

    /** Subscribes the token's Meta app to the WABA's webhooks (POST /{waba}/subscribed_apps). */
    public WhatsAppMetaCheckResult subscribe(WhatsAppMetaCheckRequest request) {
        try {
            Credentials credentials = resolve(request);
            if (credentials.businessAccountId() == null) {
                throw new MetaGraphException("MISSING_WABA", "Enter the WhatsApp Business Account ID to subscribe it.");
            }
            graph.post(credentials.baseUrl(), credentials.token(), "/" + credentials.businessAccountId() + "/subscribed_apps");
            return check(credentials);
        } catch (MetaGraphException e) {
            return WhatsAppMetaCheckResult.failure(e.getCode(), e.getMessage());
        }
    }

    private WhatsAppMetaCheckResult check(Credentials credentials) {
        if (credentials.businessAccountId() == null && credentials.phoneNumberId() == null) {
            throw new MetaGraphException("MISSING_ID", "Enter a WhatsApp Business Account ID or a phone number ID.");
        }
        List<WhatsAppMetaPhoneNumberDTO> numbers = credentials.businessAccountId() == null ? List.of() : listNumbers(credentials);
        WhatsAppMetaPhoneNumberDTO number = credentials.phoneNumberId() == null ? null : findNumber(credentials, numbers);
        List<String> apps = credentials.businessAccountId() == null ? null : subscribedApps(credentials);
        return new WhatsAppMetaCheckResult(true, null, null, numbers, number, apps);
    }

    private List<WhatsAppMetaPhoneNumberDTO> listNumbers(Credentials credentials) {
        Map<String, Object> body = graph.get(credentials.baseUrl(), credentials.token(),
                "/" + credentials.businessAccountId() + "/phone_numbers?fields=" + PHONE_FIELDS);
        return WhatsAppPayloads.mapList(body, DATA).stream().map(WhatsAppMetaService::toPhoneNumber).toList();
    }

    private WhatsAppMetaPhoneNumberDTO findNumber(Credentials credentials, List<WhatsAppMetaPhoneNumberDTO> numbers) {
        if (numbers.isEmpty()) {
            return toPhoneNumber(graph.get(credentials.baseUrl(), credentials.token(), "/" + credentials.phoneNumberId() + "?fields=" + PHONE_FIELDS));
        }
        return numbers.stream().filter(n -> Objects.equals(n.id(), credentials.phoneNumberId())).findFirst()
                .orElseThrow(() -> new MetaGraphException("NOT_IN_WABA", "That phone number isn't in this WhatsApp Business Account."));
    }

    private List<String> subscribedApps(Credentials credentials) {
        Map<String, Object> body = graph.get(credentials.baseUrl(), credentials.token(), "/" + credentials.businessAccountId() + "/subscribed_apps");
        return WhatsAppPayloads.mapList(body, DATA).stream()
                .map(app -> WhatsAppPayloads.nestedMap(app, "whatsapp_business_api_data"))
                .map(app -> WhatsAppPayloads.textOr(app.getOrDefault("name", app.get("id")), "Unnamed app"))
                .toList();
    }

    static WhatsAppMetaPhoneNumberDTO toPhoneNumber(Map<String, Object> raw) {
        return new WhatsAppMetaPhoneNumberDTO(text(raw, "id"), text(raw, "display_phone_number"), text(raw, "verified_name"),
                text(raw, "quality_rating"), text(raw, "code_verification_status"), text(raw, "name_status"), text(raw, "platform_type"));
    }

    private static String text(Map<String, Object> raw, String key) {
        return WhatsAppPayloads.textOr(raw.get(key), null);
    }

    Credentials resolve(WhatsAppMetaCheckRequest request) {
        if (request.connectorId() != null) {
            return fromConnector(request);
        }
        String token = blankToNull(request.accessToken());
        if (token == null) {
            throw new MetaGraphException("MISSING_TOKEN", "Enter the access token.");
        }
        String baseUrl = Optional.ofNullable(blankToNull(request.graphApiUrl())).orElse(DEFAULT_GRAPH_API_URL);
        return new Credentials(MetaGraphClient.validatedBaseUrl(baseUrl), token,
                metaId(request.businessAccountId(), "WhatsApp Business Account ID"), metaId(request.phoneNumberId(), "Phone number ID"));
    }

    /** A saved connector's token is used server-side only, and only by the connector's owner. */
    private Credentials fromConnector(WhatsAppMetaCheckRequest request) {
        SmsConnector connector = connectorRepository.findById(request.connectorId())
                .filter(found -> "WHATSAPP".equalsIgnoreCase(found.getProvider()) && isOwnedByCurrentUser(found))
                .orElseThrow(() -> new MetaGraphException("NOT_FOUND", "WhatsApp connector not found."));
        boolean tokenOnly = Boolean.TRUE.equals(request.tokenOnly());
        String waba = Optional.ofNullable(blankToNull(request.businessAccountId())).orElseGet(() -> tokenOnly ? null : businessAccountIdOf(connector));
        String phone = Optional.ofNullable(blankToNull(request.phoneNumberId())).orElse(tokenOnly ? null : connector.getSenderId());
        return new Credentials(MetaGraphClient.validatedBaseUrl(connector.getUrl()), connector.getKey(),
                metaId(waba, "WhatsApp Business Account ID"), metaId(phone, "Phone number ID"));
    }

    private boolean isOwnedByCurrentUser(SmsConnector connector) {
        Optional<User> user = currentUserResolver.getCurrentUser();
        return user.isPresent() && connector.getCreatedBy() != null && connector.getCreatedBy().getId().equals(user.get().getId());
    }

    private String businessAccountIdOf(SmsConnector connector) {
        if (connector.getExtraSettings() == null || connector.getExtraSettings().isBlank()) {
            return null;
        }
        try {
            return WhatsAppPayloads.textOr(objectMapper.readValue(connector.getExtraSettings(), Map.class).get("businessAccountId"), null);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** IDs go into the request path, so anything but digits is rejected rather than escaped. */
    private static String metaId(String value, String label) {
        String trimmed = blankToNull(value);
        if (trimmed != null && !META_ID.matcher(trimmed).matches()) {
            throw new MetaGraphException("INVALID_ID", label + " must be the numeric ID from Meta.");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
