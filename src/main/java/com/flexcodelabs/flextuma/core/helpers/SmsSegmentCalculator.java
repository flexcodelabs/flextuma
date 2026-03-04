package com.flexcodelabs.flextuma.core.helpers;

import java.util.HashSet;
import java.util.Set;

public class SmsSegmentCalculator {

    private SmsSegmentCalculator() {
    }

    private static final Set<Character> GSM7_CHARS = new HashSet<>();

    static {
        String gsm7Chars = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001BÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà^{}\\[~]|€";
        for (char c : gsm7Chars.toCharArray()) {
            GSM7_CHARS.add(c);
        }
    }

    private static final Set<Character> GSM7_EXTENDED_CHARS = new HashSet<>();
    static {
        String gsm7ExtChars = "^{}\\[~]|€";
        for (char c : gsm7ExtChars.toCharArray()) {
            GSM7_EXTENDED_CHARS.add(c);
        }
    }

    public static SmsSegmentResult calculate(String message) {
        if (message == null || message.isEmpty()) {
            return new SmsSegmentResult(0, true, 0);
        }

        boolean isGsm7 = true;
        int gsm7Length = 0;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            if (!GSM7_CHARS.contains(c)) {
                isGsm7 = false;
                break;
            }
            gsm7Length += GSM7_EXTENDED_CHARS.contains(c) ? 2 : 1;
        }

        int segments;
        int finalLength;

        if (isGsm7) {
            finalLength = gsm7Length;
            segments = finalLength <= 160 ? 1 : (int) Math.ceil((double) finalLength / 153);
        } else {
            finalLength = message.length();
            segments = finalLength <= 70 ? 1 : (int) Math.ceil((double) finalLength / 67);
        }

        return new SmsSegmentResult(segments, isGsm7, finalLength);
    }
}
