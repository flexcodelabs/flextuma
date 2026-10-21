package com.flexcodelabs.flextuma.modules.whatsapp.services.webhook;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Null-safe readers for Meta's webhook payload shape: {@code {entry: [{id, changes: [{field, value}]}]}}. */
public final class WhatsAppPayloads {
    public static final String VALUE = "value";
    public static final String MESSAGES = "messages";
    public static final String STATUSES = "statuses";
    public static final String STATUS = "status";
    public static final String TEMPLATE_STATUS_FIELD = "message_template_status_update";
    private static final String UNKNOWN = "unknown";
    private static final int VISIBLE_PHONE_DIGITS = 4;

    private WhatsAppPayloads() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mapList(Map<String, Object> source, String key) {
        Object items = source.get(key);
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    public static Map<String, Object> firstOf(Map<String, Object> value, String key) {
        List<Map<String, Object>> items = mapList(value, key);
        return items.isEmpty() ? Map.of() : items.get(0);
    }

    public static List<Map<String, Object>> entries(Map<String, Object> payload) {
        return mapList(payload, "entry");
    }

    public static List<Map<String, Object>> changes(Map<String, Object> payload) {
        return entries(payload).stream().flatMap(entry -> mapList(entry, "changes").stream()).toList();
    }

    public static Map<String, Object> value(Map<String, Object> change) {
        return nestedMap(change, VALUE);
    }

    /** {@code value.metadata.phone_number_id} of one change; absent for WABA-level events. */
    public static Optional<String> phoneNumberId(Map<String, Object> change) {
        return text(nestedMap(value(change), "metadata").get("phone_number_id"));
    }

    /** {@code value.metadata.display_phone_number} of the first change that carries one. */
    public static Optional<String> displayPhoneNumberOfPayload(Map<String, Object> payload) {
        return changes(payload).stream()
                .map(change -> text(nestedMap(value(change), "metadata").get("display_phone_number")))
                .flatMap(Optional::stream).findFirst();
    }

    public static Optional<String> phoneNumberIdOfPayload(Map<String, Object> payload) {
        return changes(payload).stream().map(WhatsAppPayloads::phoneNumberId).flatMap(Optional::stream).findFirst();
    }

    public static Optional<String> text(Object value) {
        return Optional.ofNullable(value).map(Object::toString).filter(text -> !text.isBlank());
    }

    public static String textOr(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    public static String maskPhone(Object phoneNumber) {
        if (phoneNumber == null) {
            return UNKNOWN;
        }
        String value = phoneNumber.toString();
        return value.length() <= VISIBLE_PHONE_DIGITS ? value : "•••• " + value.substring(value.length() - VISIBLE_PHONE_DIGITS);
    }

    public static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** "[code] message" from the first entry of a status's {@code errors}; null when there is none. */
    public static String extractErrorMessage(Map<String, Object> raw) {
        Map<String, Object> first = firstOf(raw, "errors");
        if (first.isEmpty()) {
            return null;
        }
        String text = Stream.of(first.get("message"), first.get("title"))
                .flatMap(item -> text(item).stream()).findFirst().orElse("WhatsApp delivery failed");
        Object code = first.get("code");
        return code != null ? "[" + code + "] " + text : text;
    }
}
