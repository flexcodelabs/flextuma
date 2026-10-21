package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

import java.util.UUID;

/**
 * Credentials to check against Meta. Either supply {@code accessToken} (+ {@code graphApiUrl}) for
 * a number not yet saved, or {@code connectorId} to reuse a saved connector's token, which never
 * leaves the server. With {@code tokenOnly}, only the connector's token is borrowed -- its own
 * WABA and phone number are not used as defaults -- so a saved token can look up another number.
 */
public record WhatsAppMetaCheckRequest(
        String graphApiUrl,
        String accessToken,
        String businessAccountId,
        String phoneNumberId,
        UUID connectorId,
        Boolean tokenOnly) {

    public WhatsAppMetaCheckRequest(String graphApiUrl, String accessToken, String businessAccountId, String phoneNumberId, UUID connectorId) {
        this(graphApiUrl, accessToken, businessAccountId, phoneNumberId, connectorId, null);
    }
}
