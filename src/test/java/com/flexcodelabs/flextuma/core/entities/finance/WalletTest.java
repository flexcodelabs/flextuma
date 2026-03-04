package com.flexcodelabs.flextuma.core.entities.finance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class WalletTest {

    @Test
    void testDefaultConstructorAndInitialValues() {
        Wallet wallet = new Wallet();
        assertEquals(BigDecimal.ZERO, wallet.getBalance());
        assertEquals("TZS", wallet.getCurrency());
        assertNull(wallet.getVersion());
    }

    @Test
    void testGettersAndSetters() {
        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("150.75"));
        wallet.setCurrency("USD");
        wallet.setVersion(2L);

        assertEquals(new BigDecimal("150.75"), wallet.getBalance());
        assertEquals("USD", wallet.getCurrency());
        assertEquals(2L, wallet.getVersion());
    }

    @Test
    void testAllArgsConstructor() {
        Wallet wallet = new Wallet(new BigDecimal("200.00"), "KES", 1L);
        assertEquals(new BigDecimal("200.00"), wallet.getBalance());
        assertEquals("KES", wallet.getCurrency());
        assertEquals(1L, wallet.getVersion());
    }
}
