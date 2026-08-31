package com.flexcodelabs.flextuma.core.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Encrypts provider credentials at rest. Key material is supplied by the deployment, never the database. */
@Slf4j @Component
public class ConnectorSecretCrypto {
    private static final String PREFIX = "enc:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile SecretKeySpec encryptionKey;
    @Value("${flextuma.connector-secrets.encryption-key:}") private String configuredKey;

    @PostConstruct void configure() {
        if (configuredKey == null || configuredKey.isBlank()) { log.warn("Connector credential encryption is not configured. New connector secrets will be rejected until FLEXTUMA_CONNECTOR_ENCRYPTION_KEY is set."); return; }
        byte[] key = Base64.getDecoder().decode(configuredKey);
        if (key.length != 32) throw new IllegalStateException("FLEXTUMA_CONNECTOR_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
        encryptionKey = new SecretKeySpec(key, "AES");
    }
    public static String encrypt(String value) {
        if (value == null || value.isBlank() || value.startsWith(PREFIX)) return value;
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length); System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) { throw new IllegalStateException("Unable to encrypt connector credential", e); }
    }
    public static String decrypt(String value) {
        if (value == null || !value.startsWith(PREFIX)) return value; // Existing plaintext rows remain readable until re-saved.
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length())); byte[] iv = Arrays.copyOfRange(combined, 0, 12); byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Unable to decrypt connector credential", e); }
    }
    private static SecretKeySpec requireKey() { if (encryptionKey == null) throw new IllegalStateException("FLEXTUMA_CONNECTOR_ENCRYPTION_KEY must be configured before storing connector credentials"); return encryptionKey; }
}
