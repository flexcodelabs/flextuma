package com.flexcodelabs.flextuma.core.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import com.flexcodelabs.flextuma.core.config.auth.AuthCookieProperties;

@ExtendWith(MockitoExtension.class)
class CookieServiceTest {

    @Mock
    private AuthCookieProperties properties;

    private CookieService service;

    @BeforeEach
    void setUp() {
        service = new CookieService(properties);
    }

    @Test
    void createAuthCookie_shouldReturnValidCookie() {
        when(properties.name()).thenReturn("SESSION");
        when(properties.path()).thenReturn("/");
        when(properties.secure()).thenReturn(true);
        when(properties.httpOnly()).thenReturn(true);
        when(properties.maxAge()).thenReturn(java.time.Duration.ofSeconds(3600));
        when(properties.sameSite()).thenReturn("Strict");

        ResponseCookie cookie = service.createAuthCookie();

        assertNotNull(cookie);
        assertEquals("SESSION", cookie.getName());
        assertFalse(cookie.getValue().isEmpty());
        assertEquals("/", cookie.getPath());
        assertEquals(3600, cookie.getMaxAge().getSeconds());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    void deleteAuthCookie_shouldReturnEmptyCookie() {
        when(properties.name()).thenReturn("SESSION");
        when(properties.path()).thenReturn("/");
        when(properties.secure()).thenReturn(true);
        when(properties.httpOnly()).thenReturn(true);
        when(properties.sameSite()).thenReturn("Strict");

        ResponseCookie cookie = service.deleteAuthCookie();

        assertNotNull(cookie);
        assertEquals("SESSION", cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertEquals(0, cookie.getMaxAge().getSeconds());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertTrue(cookie.isHttpOnly());
    }
}
