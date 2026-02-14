package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataSanitizerTest {

    @Test
    void sanitize_shouldReturnAllData_whenFieldsAreEmpty() {
        Pagination<String> pagination = new Pagination<>(1, 10L, 10, List.of("item1", "item2"));

        Map<String, Object> result = DataSanitizer.sanitize(pagination, "", "items");

        assertEquals(1, result.get("page"));
        assertEquals(10L, result.get("total"));
        assertEquals(10, result.get("pageSize"));
        assertEquals(List.of("item1", "item2"), result.get("items"));
    }

    @Test
    void sanitize_shouldReturnAllData_whenFieldsAreWildcard() {
        Pagination<String> pagination = new Pagination<>(1, 10L, 10, List.of("item1"));

        Map<String, Object> result = DataSanitizer.sanitize(pagination, "*", "items");

        assertEquals(List.of("item1"), result.get("items"));
    }

    @Test
    void sanitize_shouldFilterFields_whenFieldsAreSpecified() {
        // This tests the structure setup, actual filtering happens via Jackson
        // serialization which is harder to test in isolation without ObjectMapper
        // Ideally DataSanitizer should be tested with ObjectMapper integration, but we
        // can verify the _fieldTree
        Pagination<String> pagination = new Pagination<>(1, 10L, 10, List.of("item1"));

        Map<String, Object> result = DataSanitizer.sanitize(pagination, "id,name", "items");

        assertTrue(result.containsKey("_fieldTree"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldTree = (Map<String, Object>) result.get("_fieldTree");
        assertTrue(fieldTree.containsKey("id"));
        assertTrue(fieldTree.containsKey("name"));
    }
}
