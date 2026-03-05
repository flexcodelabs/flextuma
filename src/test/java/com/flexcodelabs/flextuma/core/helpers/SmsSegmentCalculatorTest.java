package com.flexcodelabs.flextuma.core.helpers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsSegmentCalculatorTest {

    @Test
    void testEmptyMessage() {
        SmsSegmentCalculator calculator = new SmsSegmentCalculator();
        SmsSegmentResult result = calculator.calculate("");
        assertEquals(0, result.segments());
        assertEquals(0, result.length());
        assertTrue(result.isGsm7());
        assertEquals(160, result.charactersRemaining());
    }

    @Test
    void testNullMessage() {
        SmsSegmentCalculator calculator = new SmsSegmentCalculator();
        SmsSegmentResult result = calculator.calculate(null);
        assertEquals(0, result.segments());
        assertEquals(0, result.length());
        assertTrue(result.isGsm7());
        assertEquals(160, result.charactersRemaining());
    }

    @ParameterizedTest
    @CsvSource({
            // GSM-7 Test Cases
            "Hello World, 1, true, 11, 149",
            "This is a standard message that fits exactly in one single segment without any special formatting thus taking one segment 0123456789 0123456789 012345678, 1, true, 153, 7",
            "This is a standard message that exceeds one single segment so it will be split into two segments as it has more than strictly one hundred and sixty character length in gsm7., 2, true, 173, 133",
            "Hello {}, 1, true, 10, 150",
            "Habari ya asubuhi, 1, true, 17, 143",
            "Swahili text without weird chars, 1, true, 32, 128",

            // Unicode Test Cases
            "Hello 🌍, 1, false, 8, 62",
            "This message contains special characters \u00E1 which makes it non gsm7 actually á is extended in gsm wait no á is not in default gsm7 alphabet, 3, false, 138, 63",
            "Mambo vipi 🎉 Tuna ofa mpya kwako! Njoo ujipatie punguzo la asilimia kumi kwa kila bidhaa utakayonunua., 2, false, 103, 31"
    })
    void testSegmentCalculations(String message, int expectedSegments, boolean expectedGsm7, int expectedLength,
            int expectedRemaining) {
        SmsSegmentCalculator calculator = new SmsSegmentCalculator();
        SmsSegmentResult result = calculator.calculate(message);
        assertEquals(expectedSegments, result.segments());
        assertEquals(expectedGsm7, result.isGsm7());
        assertEquals(expectedLength, result.length());
        assertEquals(expectedRemaining, result.charactersRemaining());
    }
}
