package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

import java.util.List;

/**
 * Outcome of syncing templates, one entry per WhatsApp Business Account. A failing account (an
 * expired token, a missing WABA id) is reported in {@code error} without stopping the others.
 */
public record WhatsAppTemplateSyncResultDTO(int synced, int removed, List<Account> accounts) {

    /**
     * @param businessAccountId null for numbers with no WABA id set
     * @param phoneNumberIds    the numbers (connector sender ids) in this account
     */
    public record Account(String businessAccountId, List<String> phoneNumberIds, int synced, int removed, String error) {
    }
}
