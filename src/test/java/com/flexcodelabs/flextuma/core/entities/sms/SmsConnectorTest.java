package com.flexcodelabs.flextuma.core.entities.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmsConnectorTest {

    @Test
    void getMaskedKey_shouldReturnNull_whenKeyIsNull() {
        SmsConnector connector = new SmsConnector();
        connector.setKey(null);

        String masked = connector.getMaskedKey();

        assertNull(masked);
    }

    @Test
    void getMaskedKey_shouldReturnNull_whenKeyIsEmpty() {
        SmsConnector connector = new SmsConnector();
        connector.setKey("");

        String masked = connector.getMaskedKey();

        assertNull(masked);
    }

    @Test
    void getMaskedKey_shouldReturnMasked_whenKeyIsPopulated() {
        SmsConnector connector = new SmsConnector();
        connector.setKey("my-secret-key-123");

        String masked = connector.getMaskedKey();

        assertEquals("******", masked);
    }

    @Test
    void getMaskedSecret_shouldReturnNull_whenSecretIsNull() {
        SmsConnector connector = new SmsConnector();
        connector.setSecret(null);

        String masked = connector.getMaskedSecret();

        assertNull(masked);
    }

    @Test
    void getMaskedSecret_shouldReturnNull_whenSecretIsEmpty() {
        SmsConnector connector = new SmsConnector();
        connector.setSecret("");

        String masked = connector.getMaskedSecret();

        assertNull(masked);
    }

    @Test
    void getMaskedSecret_shouldReturnMasked_whenSecretIsPopulated() {
        SmsConnector connector = new SmsConnector();
        connector.setSecret("my-secret-value-456");

        String masked = connector.getMaskedSecret();

        assertEquals("******", masked);
    }

    @Test
    void isDefault_shouldBeTrueByDefault() {
        SmsConnector connector = new SmsConnector();

        assertTrue(connector.getIsDefault());
    }
}
