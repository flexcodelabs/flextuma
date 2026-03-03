package com.flexcodelabs.flextuma.core.webhooks;

import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;

/**
 * Normalised result from a DLR payload parse.
 *
 * @param messageId the provider-assigned message identifier (used to locate the
 *                  SmsLog)
 * @param status    the mapped internal status
 * @param rawStatus the raw status string from the provider (stored for audit)
 */
public record DlrResult(String messageId, SmsLogStatus status, String rawStatus) {
}
