package com.flexcodelabs.flextuma.core.helpers;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SmsSegmentCalculator {

    private int gsm7MaxLength = 160;
    private int gsm7MultipartLength = 153;
    private int ucs2MaxLength = 70;
    private int ucs2MultipartLength = 67;
    private BigDecimal pricePerSegment = BigDecimal.ONE;

    @Value("${app.sms.segment.gsm7.max:160}")
    public void setGsm7MaxLength(int gsm7MaxLength) {
        this.gsm7MaxLength = gsm7MaxLength;
    }

    @Value("${app.sms.segment.gsm7.multipart:153}")
    public void setGsm7MultipartLength(int gsm7MultipartLength) {
        this.gsm7MultipartLength = gsm7MultipartLength;
    }

    @Value("${app.sms.segment.ucs2.max:70}")
    public void setUcs2MaxLength(int ucs2MaxLength) {
        this.ucs2MaxLength = ucs2MaxLength;
    }

    @Value("${app.sms.segment.ucs2.multipart:67}")
    public void setUcs2MultipartLength(int ucs2MultipartLength) {
        this.ucs2MultipartLength = ucs2MultipartLength;
    }

    @Value("${flextuma.sms.price-per-segment:1.0}")
    public void setPricePerSegment(BigDecimal pricePerSegment) {
        this.pricePerSegment = pricePerSegment;
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

    public SmsSegmentResult calculate(String message) {
        if (message == null || message.isEmpty()) {
            return new SmsSegmentResult(0, true, 0, gsm7MaxLength, BigDecimal.ZERO, pricePerSegment);
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
        int maxCapacity;

        if (isGsm7) {
            finalLength = gsm7Length;
            if (finalLength <= gsm7MaxLength) {
                segments = 1;
                maxCapacity = gsm7MaxLength;
            } else {
                segments = (int) Math.ceil((double) finalLength / gsm7MultipartLength);
                maxCapacity = segments * gsm7MultipartLength;
            }
        } else {
            finalLength = message.length();
            if (finalLength <= ucs2MaxLength) {
                segments = 1;
                maxCapacity = ucs2MaxLength;
            } else {
                segments = (int) Math.ceil((double) finalLength / ucs2MultipartLength);
                maxCapacity = segments * ucs2MultipartLength;
            }
        }

        int charactersRemaining = maxCapacity - finalLength;
        BigDecimal cost = pricePerSegment.multiply(BigDecimal.valueOf(segments));
        return new SmsSegmentResult(segments, isGsm7, finalLength, charactersRemaining, cost, pricePerSegment);
    }
}
