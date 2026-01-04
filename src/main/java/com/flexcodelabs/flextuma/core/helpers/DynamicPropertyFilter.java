package com.flexcodelabs.flextuma.core.helpers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

public class DynamicPropertyFilter extends SimpleBeanPropertyFilter {

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer)
            throws Exception {
        HttpServletRequest request = getCurrentRequest();
        String fields = (request != null) ? request.getParameter("fields") : null;

        String name = writer.getName();
        if (isMetadata(name) || fields == null || fields.isBlank() || fields.equals("*")) {
            writer.serializeAsField(pojo, jgen, provider);
            return;
        }

        Map<String, Object> tree = getFieldTree(request, fields);

        if (tree.containsKey(name)) {
            writer.serializeAsField(pojo, jgen, provider);
        }
    }

    private boolean isMetadata(String name) {
        return name.equals("id") || name.equals("page") || name.equals("total")
                || name.equals("pageSize");
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return (attrs != null) ? attrs.getRequest() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFieldTree(HttpServletRequest request, String fields) {
        Map<String, Object> tree = (Map<String, Object>) request.getAttribute("FIELD_TREE");
        if (tree == null) {
            tree = (Map<String, Object>) FieldParser.parse(fields);
            request.setAttribute("FIELD_TREE", tree);
        }
        return tree;
    }
}