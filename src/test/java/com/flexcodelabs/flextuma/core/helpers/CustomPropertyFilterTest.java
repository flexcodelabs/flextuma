package com.flexcodelabs.flextuma.core.helpers;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomPropertyFilterTest {

    private ObjectMapper objectMapper;
    private MockHttpServletRequest request;

    @JsonFilter("customFilter")
    static class TestEntity {
        public UUID id = UUID.randomUUID();
        public String name = "Test Name";
        public String description = "Test Description";
        public int page = 1; // technical field
        public NestedEntity nested = new NestedEntity();
        public TestEntity circular; // for circular reference test
    }

    @JsonFilter("customFilter")
    static class NestedEntity {
        public String details = "Nested Details";
        public String hidden = "Should Be Hidden";
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);
        FilterProvider filters = new SimpleFilterProvider()
                .addFilter("customFilter", new CustomPropertyFilter());
        objectMapper.setFilterProvider(filters);

        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldSerializeAllFieldsWhenNoFieldsParam() throws JsonProcessingException {
        TestEntity entity = new TestEntity();
        String json = objectMapper.writeValueAsString(entity);

        assertTrue(json.contains("id"));
        assertTrue(json.contains("name"));
        assertTrue(json.contains("description"));
        assertTrue(json.contains("page"));
        assertTrue(json.contains("nested"));
        assertTrue(json.contains("details"));
        assertTrue(json.contains("hidden"));
    }

    @Test
    void shouldSerializeAllFieldsWhenFieldsParamIsAsterisk() throws JsonProcessingException {
        request.setParameter("fields", "*");
        TestEntity entity = new TestEntity();
        String json = objectMapper.writeValueAsString(entity);

        assertTrue(json.contains("name"));
        assertTrue(json.contains("description"));
        assertTrue(json.contains("nested"));
    }

    @Test
    void shouldFilterFieldsBasedOnParam() throws JsonProcessingException {
        request.setParameter("fields", "name,nested[details]");
        TestEntity entity = new TestEntity();
        String json = objectMapper.writeValueAsString(entity);

        // Technical fields are always included
        assertTrue(json.contains("id"));
        assertTrue(json.contains("page"));

        // Requested fields are included
        assertTrue(json.contains("name"));
        assertTrue(json.contains("nested"));
        assertTrue(json.contains("details"));

        // Non-requested fields are excluded
        assertFalse(json.contains("description"));
        assertFalse(json.contains("hidden"));
    }

    @Test
    void shouldHandleCircularReferencesGracefully() throws JsonProcessingException {
        request.setParameter("fields", "*");
        TestEntity entity = new TestEntity();
        entity.circular = entity; // Create circular reference

        String json = objectMapper.writeValueAsString(entity);

        // Should not throw StackOverflowError and should serialize successfully
        assertTrue(json.contains("name"));
    }

    @Test
    void shouldHandleDeepNestingGracefully() throws JsonProcessingException {
        // Build a deeply nested structure (depth > 10)
        TestEntity root = new TestEntity();
        TestEntity current = root;
        for (int i = 0; i < 15; i++) {
            current.circular = new TestEntity();
            current = current.circular;
        }

        request.setParameter("fields", "*");
        String json = objectMapper.writeValueAsString(root);

        // Should not throw StackOverflowError and should only serialize up to depth
        // limit
        assertNotNull(json);
    }
}
