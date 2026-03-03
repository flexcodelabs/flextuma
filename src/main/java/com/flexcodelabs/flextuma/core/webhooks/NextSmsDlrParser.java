package com.flexcodelabs.flextuma.core.webhooks;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;

@Component
public class NextSmsDlrParser implements DlrParser {

    private static final Set<String> DELIVERED_STATUSES = Set.of("delivrd", "delivered", "sent");
    private static final Set<String> FAILED_STATUSES = Set.of("failed", "undeliv", "rejectd", "expired");

    @Override
    public String getProvider() {
        return "NEXT";
    }

    @Override
    public DlrResult parse(Map<String, Object> payload) {
        String messageId = String.valueOf(
                payload.getOrDefault("message_id",
                        payload.getOrDefault("messageId", "")));

        String rawStatus = String.valueOf(payload.getOrDefault("status", "")).toLowerCase();

        SmsLogStatus status = null;
        if (DELIVERED_STATUSES.contains(rawStatus)) {
            status = SmsLogStatus.SENT;
        } else if (FAILED_STATUSES.contains(rawStatus)) {
            status = SmsLogStatus.FAILED;
        }

        return new DlrResult(messageId, status, rawStatus);
    }
}
