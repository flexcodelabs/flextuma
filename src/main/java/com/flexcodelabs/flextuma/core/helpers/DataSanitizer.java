package com.flexcodelabs.flextuma.core.helpers;

import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import java.util.*;

public class DataSanitizer {

    private DataSanitizer() {
    }

    public static <T> Map<String, Object> sanitize(Pagination<T> pagination, String fields, String propertyName) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", pagination.getPage());
        response.put("total", pagination.getTotal());
        response.put("pageSize", pagination.getPageSize());
        response.put(propertyName, pagination.getData());

        if (fields == null || fields.isBlank() || "*".equals(fields)) {
            return response;
        }

        Map<String, Object> fieldTree = parseFields(fields);

        fieldTree.put("page", new HashMap<>());
        fieldTree.put("total", new HashMap<>());
        fieldTree.put("pageSize", new HashMap<>());
        fieldTree.put(propertyName, fieldTree.getOrDefault(propertyName, new HashMap<>()));

        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter("CustomFilter", new DynamicPropertyFilter());

        response.put("_filterProvider", filterProvider);
        response.put("_fieldTree", fieldTree);

        return response;
    }

    private static Map<String, Object> parseFields(String fields) {
        Map<String, Object> root = new HashMap<>();
        Deque<Map<String, Object>> stack = new ArrayDeque<>();
        stack.push(root);
        StringBuilder buffer = new StringBuilder();

        for (char c : fields.toCharArray()) {
            if (c == '[') {
                String key = buffer.toString().trim();
                Map<String, Object> child = new HashMap<>();
                stack.peek().put(key, child);
                stack.push(child);
                buffer.setLength(0);
            } else if (c == ']') {
                stack.pop();
                buffer.setLength(0);
            } else if (c == ',') {
                String key = buffer.toString().trim();
                if (!key.isEmpty())
                    stack.peek().put(key, new HashMap<>());
                buffer.setLength(0);
            } else {
                buffer.append(c);
            }
        }
        String lastKey = buffer.toString().trim();
        if (!lastKey.isEmpty())
            root.put(lastKey, new HashMap<>());
        return root;
    }
}