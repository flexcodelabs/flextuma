package com.flexcodelabs.flextuma.core.helpers;

public class MaskingUtil {

    private MaskingUtil() {
    }

    public static String mask(String value) {
        return (value != null && !value.isEmpty()) ? "******" : null;
    }
}
