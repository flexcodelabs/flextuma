package com.flexcodelabs.flextuma.core.helpers;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class PaginationHelperTest {

    @Test
    void getPageable_withNulls_shouldReturnDefaultPageable() {
        Pageable pageable = PaginationHelper.getPageable(null, null);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(15, pageable.getPageSize());
        assertEquals(Sort.by("created").descending(), pageable.getSort());
    }

    @Test
    void getPageable_withValues_shouldReturnRequestedPageable() {
        Pageable pageable = PaginationHelper.getPageable(5, 20);
        assertEquals(5, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
    }

    @Test
    void getPageable_withInvalidValues_shouldReturnCorrectPageable() {
        Pageable pageable = PaginationHelper.getPageable(-1, 0);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(15, pageable.getPageSize());
    }

    @Test
    void testPrivateConstructor()
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<PaginationHelper> constructor = PaginationHelper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PaginationHelper instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
