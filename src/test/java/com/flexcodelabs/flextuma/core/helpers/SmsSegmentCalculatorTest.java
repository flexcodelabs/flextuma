package com.flexcodelabs.flextuma.core.helpers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsSegmentCalculatorTest {

    @Test
    void testEmptyMessage() {
        SmsSegmentResult result = SmsSegmentCalculator.calculate("");
        assertEquals(0, result.segments());
        assertEquals(0, result.length());
        assertTrue(result.isGsm7());
    }

    @Test
    void testNullMessage() {
        SmsSegmentResult result = SmsSegmentCalculator.calculate(null);
        assertEquals(0, result.segments());
        assertEquals(0, result.length());
        assertTrue(result.isGsm7());
    }

    @ParameterizedTest
    @CsvSource({
            // GSM-7 Test Cases
            "Hello World, 1, true, 11",
            "This is a standard message that fits exactly in one single segment without any special formatting thus taking one segment 0123456789 0123456789 012345678, 1, true, 153",
            "This is a standard message that exceeds one single segment so it will be split into two segments as it has more than strictly one hundred and sixty character length in gsm7., 2, true, 173",
            "Hello {}, 1, true, 10",
            "Habari ya asubuhi, 1, true, 17",
            "Swahili text without weird chars, 1, true, 32",

            // Unicode Test Cases
            "Hello 🌍, 1, false, 8",
            "This message contains special characters \u00E1 which makes it non gsm7 actually á is extended in gsm wait no á is not in default gsm7 alphabet, 3, false, 138",
            "Mambo vipi 🎉 Tuna ofa mpya kwako! Njoo ujipatie punguzo la asilimia kumi kwa kila bidhaa utakayonunua., 2, false, 103"
    })
    void testSegmentCalculations(String message, int expectedSegments, boolean expectedGsm7, int expectedLength) {
        SmsSegmentResult result = SmsSegmentCalculator.calculate(message);
        assertEquals(expectedSegments, result.segments());
        assertEquals(expectedGsm7, result.isGsm7());
        assertEquals(expectedLength, result.length());
    }
}
