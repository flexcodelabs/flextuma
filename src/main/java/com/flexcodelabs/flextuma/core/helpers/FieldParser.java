package com.flexcodelabs.flextuma.core.helpers;

import java.util.*;

public class FieldParser {
    public static Set<String> parse(String fields) {
        Set<String> paths = new HashSet<>();
        if (fields == null || fields.isBlank())
            return paths;

        Stack<String> stack = new Stack<>();
        StringBuilder buffer = new StringBuilder();

        for (char c : fields.toCharArray()) {
            if (c == '[') {
                String prefix = stack.isEmpty() ? "" : String.join(".", stack) + ".";
                String currentField = buffer.toString().trim();
                paths.add(prefix + currentField);
                stack.push(currentField);
                buffer.setLength(0);
            } else if (c == ']') {
                String currentField = buffer.toString().trim();
                if (!currentField.isEmpty()) {
                    String prefix = stack.isEmpty() ? "" : String.join(".", stack) + ".";
                    paths.add(prefix + currentField);
                }
                stack.pop();
                buffer.setLength(0);
            } else if (c == ',') {
                String currentField = buffer.toString().trim();
                if (!currentField.isEmpty()) {
                    String prefix = stack.isEmpty() ? "" : String.join(".", stack) + ".";
                    paths.add(prefix + currentField);
                }
                buffer.setLength(0);
            } else {
                buffer.append(c);
            }
        }

        if (buffer.length() > 0) {
            String prefix = stack.isEmpty() ? "" : String.join(".", stack) + ".";
            paths.add(prefix + buffer.toString().trim());
        }
        return paths;
    }
}