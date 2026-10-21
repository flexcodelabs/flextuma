package com.flexcodelabs.flextuma.modules.retention.dtos;

/** Null or 0 resets to the platform default. */
public record DataRetentionPolicyUpdateDTO(Integer whatsappEventRetentionDays) {
}
