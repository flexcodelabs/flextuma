package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

import java.util.UUID;

import lombok.Builder;

/** Total WhatsApp media storage for one tenant: an organisation, or an individual user for
 * org-less accounts. */
@Builder
public record WhatsAppTenantStorageUsageDTO(
        UUID tenantId,
        String tenantLabel,
        long totalBytes,
        long mediaCount) {
}
