package com.flexcodelabs.flextuma.core.helpers;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FieldParserTest {

    @Test
    void parse_shouldReturnEmptySet_whenInputIsNull() {
        Set<String> result = FieldParser.parse(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_shouldReturnEmptySet_whenInputIsBlank() {
        Set<String> result = FieldParser.parse("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_shouldParseSimpleFields() {
        Set<String> result = FieldParser.parse("id,name");
        assertTrue(result.contains("id"));
        assertTrue(result.contains("name"));
        assertEquals(2, result.size());
    }

    @Test
    void parse_shouldParseNestedFields() {
        Set<String> result = FieldParser.parse("id,posts[id,title]");
        assertTrue(result.contains("id"));
        assertTrue(result.contains("posts"));
        assertTrue(result.contains("posts.id"));
        assertTrue(result.contains("posts.title"));
        assertEquals(4, result.size());
    }
}
