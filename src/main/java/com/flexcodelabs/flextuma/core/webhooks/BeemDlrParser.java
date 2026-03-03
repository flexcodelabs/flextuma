package com.flexcodelabs.flextuma.core.webhooks;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;

@Component
public class BeemDlrParser implements DlrParser {

    private static final Set<String> DELIVERED_STATUSES = Set.of("delivered", "sent");
    private static final Set<String> FAILED_STATUSES = Set.of("failed", "expired", "rejected", "aborted", "undelivered",
            "cancelled", "deleted");

    @Override
    public String getProvider() {
        return "BEEM";
    }

    @Override
    public DlrResult parse(Map<String, Object> payload) {
        String messageId = String.valueOf(payload.getOrDefault("messageID", ""));
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
