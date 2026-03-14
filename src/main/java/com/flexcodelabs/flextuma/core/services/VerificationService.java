package com.flexcodelabs.flextuma.core.services;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VerificationService {

    private final ConcurrentHashMap<String, VerificationData> verificationCodes = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${flextuma.verification.code-expiry-minutes:10}")
    private int codeExpiryMinutes;

    @Value("${flextuma.verification.code-length:6}")
    private int codeLength;

    public String generateVerificationCode(String identifier) {
        String code = generateNumericCode(codeLength);
        VerificationData data = new VerificationData(code, LocalDateTime.now().plusMinutes(codeExpiryMinutes));
        verificationCodes.put(identifier, data);
        return code;
    }

    public boolean verifyCode(String identifier, String providedCode) {
        VerificationData data = verificationCodes.get(identifier);
        if (data == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(data.expiryTime)) {
            verificationCodes.remove(identifier);
            return false;
        }

        boolean isValid = data.code.equals(providedCode);
        if (isValid) {
            verificationCodes.remove(identifier);
        }

        return isValid;
    }

    public boolean resendCode(String identifier) {
        // Remove existing code if any
        verificationCodes.remove(identifier);
        // Generate new code
        generateVerificationCode(identifier);
        return true;
    }

    public boolean isCodeExpired(String identifier) {
        VerificationData data = verificationCodes.get(identifier);
        return data == null || LocalDateTime.now().isAfter(data.expiryTime);
    }

    public long getRemainingTimeMinutes(String identifier) {
        VerificationData data = verificationCodes.get(identifier);
        if (data == null)
            return 0;

        long remainingMinutes = java.time.Duration.between(LocalDateTime.now(), data.expiryTime).toMinutes();
        return Math.max(0, remainingMinutes);
    }

    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

    public void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        verificationCodes.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiryTime));
    }

    private static class VerificationData {
        final String code;
        final LocalDateTime expiryTime;

        VerificationData(String code, LocalDateTime expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }
    }
}
