package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

import java.util.List;

/**
 * Outcome of checking credentials against Meta. {@code ok} is false with {@code error} set when
 * Meta rejected them; the HTTP status stays 200 so the UI can show the reason inline.
 *
 * @param phoneNumbers every number in the WhatsApp Business Account, when one was given
 * @param phoneNumber  the requested number, when a phone number ID was given
 * @param subscribedApps names of Meta apps receiving this WABA's webhooks; null when not checked
 */
public record WhatsAppMetaCheckResult(
        boolean ok,
        String error,
        String errorCode,
        List<WhatsAppMetaPhoneNumberDTO> phoneNumbers,
        WhatsAppMetaPhoneNumberDTO phoneNumber,
        List<String> subscribedApps) {

    public static WhatsAppMetaCheckResult failure(String errorCode, String error) {
        return new WhatsAppMetaCheckResult(false, error, errorCode, List.of(), null, null);
    }
}
