package com.flexcodelabs.flextuma.core.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JacksonConfigTest {

    private final JacksonConfig config = new JacksonConfig();
    private final ObjectMapper objectMapper = config.objectMapper();

    @Test
    void objectMapper_shouldExcludeNullValues() throws JsonProcessingException {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test");
        data.put("value", null);

        String json = objectMapper.writeValueAsString(data);

        assertFalse(json.contains("value"));
        assertTrue(json.contains("name"));
    }

    @Test
    void objectMapper_shouldNotWriteDatesAsTimestamps() {
        assertFalse(objectMapper.getSerializationConfig()
                .isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    void objectMapper_shouldNotFailOnEmptyBeans() {
        assertFalse(objectMapper.getSerializationConfig()
                .isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
    }

    @Test
    void objectMapper_shouldHaveNonNullSerializationInclusion() {
        assertEquals(JsonInclude.Include.NON_NULL,
                objectMapper.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion());
    }

    @Test
    void objectMapper_shouldSerializeDatesAsIso8601() throws JsonProcessingException {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.of(2024, 1, 1, 12, 0, 0));

        String json = objectMapper.writeValueAsString(data);

        assertTrue(json.contains("2024-01-01"));
        assertFalse(json.matches(".*\\d{13}.*")); // Not a timestamp
    }

    static class EmptyBean {
        // Empty class to test FAIL_ON_EMPTY_BEANS is disabled
    }

    @Test
    void objectMapper_shouldSerializeEmptyBeanWithoutError() throws JsonProcessingException {
        EmptyBean bean = new EmptyBean();
        String json = objectMapper.writeValueAsString(bean);
        assertEquals("{}", json);
    }
}
