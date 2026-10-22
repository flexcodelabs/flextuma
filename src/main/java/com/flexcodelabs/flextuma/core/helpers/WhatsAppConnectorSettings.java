package com.flexcodelabs.flextuma.core.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;

/** Reads WhatsApp-specific values from a connector's free-form extraSettings JSON. */
public final class WhatsAppConnectorSettings {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WhatsAppConnectorSettings() {
    }

    /** The WhatsApp Business Account (WABA) id, or null when unset or unparsable. Templates
     * belong to the WABA, so every number (connector) in one account shares them. */
    public static String businessAccountIdOf(SmsConnector connector) {
        String settings = connector == null ? null : connector.getExtraSettings();
        if (settings == null || settings.isBlank()) {
            return null;
        }
        try {
            JsonNode value = MAPPER.readTree(settings).get("businessAccountId");
            return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
