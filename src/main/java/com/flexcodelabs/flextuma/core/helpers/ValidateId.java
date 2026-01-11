package com.flexcodelabs.flextuma.core.helpers;

import java.util.regex.Pattern;

public class ValidateId {
    private static final Pattern UUID_REGEX = Pattern
            .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public static boolean isValid(String uuidString) {
        if (uuidString == null)
            return false;
        return UUID_REGEX.matcher(uuidString).matches();
    }
}