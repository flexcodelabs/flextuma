package com.flexcodelabs.flextuma.core.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionTypeTest {

    @Test
    void testEnumValuesAndValueOf() {
        // Test values structure
        TransactionType[] types = TransactionType.values();
        assertEquals(2, types.length);

        // Test valueOf
        assertEquals(TransactionType.CREDIT, TransactionType.valueOf("CREDIT"));
        assertEquals(TransactionType.DEBIT, TransactionType.valueOf("DEBIT"));

        // Assert specific enum ordinals/names just to guarantee they exist safely
        assertEquals("CREDIT", TransactionType.CREDIT.name());
        assertEquals("DEBIT", TransactionType.DEBIT.name());
    }
}
