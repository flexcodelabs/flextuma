package com.flexcodelabs.flextuma.core.helpers;

import java.util.*;

public class FieldParser {
    private FieldParser() {
    }

    public static Set<String> parse(String fields) {
        Set<String> paths = new HashSet<>();
        if (fields == null || fields.isBlank())
            return paths;

        Deque<String> stack = new ArrayDeque<>();
        StringBuilder buffer = new StringBuilder();

        for (char c : fields.toCharArray()) {
            if (c == '[') {
                handleOpenBracket(paths, stack, buffer);
            } else if (c == ']') {
                handleCloseBracket(paths, stack, buffer);
            } else if (c == ',') {
                handleComma(paths, stack, buffer);
            } else {
                buffer.append(c);
            }
        }

        handleRemaining(paths, stack, buffer);
        return paths;
    }

    private static void handleOpenBracket(Set<String> paths, Deque<String> stack, StringBuilder buffer) {
        String prefix = getPrefix(stack);
        String currentField = buffer.toString().trim();
        paths.add(prefix + currentField);
        stack.push(currentField);
        buffer.setLength(0);
    }

    private static void handleCloseBracket(Set<String> paths, Deque<String> stack, StringBuilder buffer) {
        String currentField = buffer.toString().trim();
        if (!currentField.isEmpty()) {
            String prefix = getPrefix(stack);
            paths.add(prefix + currentField);
        }
        stack.pop();
        buffer.setLength(0);
    }

    private static void handleComma(Set<String> paths, Deque<String> stack, StringBuilder buffer) {
        String currentField = buffer.toString().trim();
        if (!currentField.isEmpty()) {
            String prefix = getPrefix(stack);
            paths.add(prefix + currentField);
        }
        buffer.setLength(0);
    }

    private static void handleRemaining(Set<String> paths, Deque<String> stack, StringBuilder buffer) {
        if (!buffer.isEmpty()) {
            String prefix = getPrefix(stack);
            paths.add(prefix + buffer.toString().trim());
        }
    }

    private static String getPrefix(Deque<String> stack) {
        return stack.isEmpty() ? "" : String.join(".", stack) + ".";
    }
}