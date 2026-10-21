package com.flexcodelabs.flextuma.modules.retention.dtos;

import lombok.Builder;

/**
 * The caller's account retention settings plus the platform bounds they're chosen within.
 * scope is "ORGANISATION" (shared by all members) or "USER".
 */
@Builder
public record DataRetentionPolicyDTO(
        String scope,
        Integer whatsappEventRetentionDays,
        int whatsappEventDefaultDays,
        int whatsappEventMaxDays,
        int whatsappEventEffectiveDays) {
}
