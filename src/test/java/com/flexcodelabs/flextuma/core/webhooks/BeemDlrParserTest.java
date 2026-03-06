package com.flexcodelabs.flextuma.core.webhooks;

import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BeemDlrParserTest {

    private final BeemDlrParser parser = new BeemDlrParser();

    @Test
    void getProvider_shouldReturnBeem() {
        assertEquals("BEEM", parser.getProvider());
    }

    @Test
    void parse_withDeliveredStatus_shouldReturnSent() {
        Map<String, Object> payload = Map.of(
                "messageID", "12345",
                "status", "Delivered");

        DlrResult result = parser.parse(payload);

        assertEquals("12345", result.messageId());
        assertEquals(SmsLogStatus.SENT, result.status());
        assertEquals("delivered", result.rawStatus());
    }

    @Test
    void parse_withFailedStatus_shouldReturnFailed() {
        Map<String, Object> payload = Map.of(
                "messageID", "67890",
                "status", "REJECTED");

        DlrResult result = parser.parse(payload);

        assertEquals("67890", result.messageId());
        assertEquals(SmsLogStatus.FAILED, result.status());
        assertEquals("rejected", result.rawStatus());
    }

    @Test
    void parse_withUnknownStatus_shouldReturnNullStatus() {
        Map<String, Object> payload = Map.of(
                "messageID", "555",
                "status", "pEnDiNg");

        DlrResult result = parser.parse(payload);

        assertEquals("555", result.messageId());
        assertNull(result.status());
        assertEquals("pending", result.rawStatus());
    }

    @Test
    void parse_withMissingFields_shouldHandleGracefully() {
        Map<String, Object> payload = Map.of();

        DlrResult result = parser.parse(payload);

        assertEquals("", result.messageId());
        assertNull(result.status());
        assertEquals("", result.rawStatus());
    }
}
