package com.flexcodelabs.flextuma.core.entities.finance;

import com.flexcodelabs.flextuma.core.enums.TransactionType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class WalletTransactionTest {

    @Test
    void testGettersAndSetters() {
        WalletTransaction transaction = new WalletTransaction();
        Wallet wallet = new Wallet();

        transaction.setWallet(wallet);
        transaction.setType(TransactionType.CREDIT);
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setDescription("Test Credit");
        transaction.setReference("REF-123");
        transaction.setBalanceAfter(new BigDecimal("150.00"));

        assertEquals(wallet, transaction.getWallet());
        assertEquals(TransactionType.CREDIT, transaction.getType());
        assertEquals(new BigDecimal("50.00"), transaction.getAmount());
        assertEquals("Test Credit", transaction.getDescription());
        assertEquals("REF-123", transaction.getReference());
        assertEquals(new BigDecimal("150.00"), transaction.getBalanceAfter());
    }

    @Test
    void testAllArgsConstructor() {
        Wallet wallet = new Wallet();
        WalletTransaction transaction = new WalletTransaction(
                wallet,
                TransactionType.DEBIT,
                new BigDecimal("25.00"),
                "Test Debit",
                "REF-456",
                new BigDecimal("75.00"));

        assertEquals(wallet, transaction.getWallet());
        assertEquals(TransactionType.DEBIT, transaction.getType());
        assertEquals(new BigDecimal("25.00"), transaction.getAmount());
        assertEquals("Test Debit", transaction.getDescription());
        assertEquals("REF-456", transaction.getReference());
        assertEquals(new BigDecimal("75.00"), transaction.getBalanceAfter());
    }
}
