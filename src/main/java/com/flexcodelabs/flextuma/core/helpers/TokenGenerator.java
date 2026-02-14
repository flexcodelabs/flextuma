package com.flexcodelabs.flextuma.core.helpers;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public class TokenGenerator {

    private TokenGenerator() {
    }

    private static final SecureRandom secureRandom = new SecureRandom();

    private static final String ALPHANUMERIC = "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.0123456789!";

    /**
     * Generates a secure token following the pattern:
     * Base64(UUID) + RandomAlphanumericSuffix
     * 
     * @param suffixLength The length of the random characters to append
     * @return A unique, secure random string
     */
    public static String generateSecureToken(int suffixLength) {
        String uuidPart = UUID.randomUUID().toString();
        String encodedUuid = Base64.getEncoder().encodeToString(uuidPart.getBytes());

        StringBuilder suffix = new StringBuilder(suffixLength);
        for (int i = 0; i < suffixLength; i++) {
            suffix.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }

        return encodedUuid + suffix.toString();
    }

    public static String generateSecureToken() {
        return generateSecureToken(15);
    }
}