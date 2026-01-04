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

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer)
            throws Exception {
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
    }

    private String constructPath(JsonStreamContext context, String currentField) {
        Deque<String> segments = new ArrayDeque<>();
        segments.addFirst(currentField);

        JsonStreamContext parent = context.getParent();
        while (parent != null && parent.getCurrentName() != null) {
            // We ignore keys like "users" (the root array) to keep paths clean:
            // "roles.privileges"
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