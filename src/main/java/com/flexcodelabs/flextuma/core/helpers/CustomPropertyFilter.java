package com.flexcodelabs.flextuma.core.helpers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

public class CustomPropertyFilter extends SimpleBeanPropertyFilter {

    private static final ThreadLocal<Set<Integer>> SERIALIZING = ThreadLocal.withInitial(HashSet::new);

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer)
            throws Exception {

        // Detect circular references
        int pojoId = System.identityHashCode(pojo);
        Set<Integer> currentlySerializing = SERIALIZING.get();

        if (currentlySerializing.contains(pojoId)) {
            return; // Skip to prevent circular reference
        }

        currentlySerializing.add(pojoId);
        try {
            // Prevent infinite recursion - check depth
            int depth = getDepth(jgen.getOutputContext());
            if (depth > 10) {
                return; // Skip serialization if too deep
            }

            HttpServletRequest request = getCurrentRequest();
            String fieldsParam = (request != null) ? request.getParameter("fields") : null;

            // 1. Technical fields and ID are always allowed
            String name = writer.getName();
            if (fieldsParam == null || fieldsParam.isBlank() || fieldsParam.equals("*") || isTechnical(name)) {
                writer.serializeAsField(pojo, jgen, provider);
                return;
            }

            // 2. Get the allowed paths (cached per request)
            Set<String> allowedPaths = getAllowedPaths(request, fieldsParam);

            // 3. Build the current path (e.g., "roles.privileges")
            String currentPath = constructPath(jgen.getOutputContext(), name);

            // 4. Logic: Allow if the path is explicitly requested
            if (allowedPaths.contains(currentPath)) {
                writer.serializeAsField(pojo, jgen, provider);
            }
        } finally {
            currentlySerializing.remove(pojoId);
            if (currentlySerializing.isEmpty()) {
                SERIALIZING.remove();
            }
        }
    }

    private int getDepth(JsonStreamContext context) {
        int depth = 0;
        while (context != null) {
            depth++;
            context = context.getParent();
        }
        return depth;
    }

    private String constructPath(JsonStreamContext context, String currentField) {
        Deque<String> segments = new ArrayDeque<>();
        segments.addFirst(currentField);

        JsonStreamContext parent = context.getParent();
        while (parent != null && parent.getCurrentName() != null) {
            String parentName = parent.getCurrentName();
            if (!isTechnical(parentName)) {
                segments.addFirst(parentName);
            }
            parent = parent.getParent();
        }
        return String.join(".", segments);
    }

    private boolean isTechnical(String name) {
        return name.equals("id") || name.equals("page") || name.equals("total") || name.equals("pageSize");
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return (attrs != null) ? attrs.getRequest() : null;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getAllowedPaths(HttpServletRequest request, String fields) {
        Set<String> paths = (Set<String>) request.getAttribute("DHIS2_PATHS");
        if (paths == null) {
            paths = FieldParser.parse(fields);
            request.setAttribute("DHIS2_PATHS", paths);
        }
        return paths;
    }
}