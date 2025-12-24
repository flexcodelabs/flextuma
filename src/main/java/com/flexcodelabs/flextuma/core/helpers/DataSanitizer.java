package com.flexcodelabs.flextuma.core.helpers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.*;
import java.util.stream.Collectors;

public class DataSanitizer {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);;

    public static <T> Map<String, Object> sanitize(Pagination<T> pagination, String fields, String propertyName) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", pagination.getPage());
        response.put("total", pagination.getTotal());
        response.put("pageSize", pagination.getPageSize());

        List<String> fieldList = (fields != null && !fields.isBlank())
                ? Arrays.asList(fields.split(","))
                : Collections.emptyList();

        List<Map<String, Object>> sanitizedData = pagination.getData().stream()
                .map(item -> {
                    Map<String, Object> entityMap = mapper.convertValue(item, new TypeReference<Map<String, Object>>() {
                    });
                    entityMap.entrySet().removeIf(entry -> {
                        boolean isNull = entry.getValue() == null;
                        boolean isNotRequested = !fieldList.isEmpty() &&
                                !fieldList.contains(entry.getKey()) &&
                                !entry.getKey().equals("id");

                        return isNull || isNotRequested;
                    });

                    return entityMap;
                })
                .collect(Collectors.toList());

        response.put(propertyName, sanitizedData);
        return response;
    }
}