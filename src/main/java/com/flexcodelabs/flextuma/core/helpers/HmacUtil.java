package com.flexcodelabs.flextuma.core.helpers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public final class HmacUtil {

    private HmacUtil() {
    }

    public static String sha256Hex(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder result = new StringBuilder();
        for (byte b : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
