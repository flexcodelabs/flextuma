package com.flexcodelabs.flextuma.core.security;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private CustomSecurityExceptionHandler securityExceptionHandler;

    @InjectMocks
    private SecurityConfig securityConfig;

    @Test
    void passwordEncoder_shouldReturnBCryptPasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertNotNull(encoder);
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
    }

    @Test
    void passwordEncoder_shouldEncodePasswords() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String rawPassword = "testPassword123";

        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        assertNotEquals(rawPassword, encoded);
        assertTrue(encoder.matches(rawPassword, encoded));
    }

    @Test
    void cookieSerializer_shouldReturnDefaultCookieSerializer() {
        CookieSerializer serializer = securityConfig.cookieSerializer();

        assertNotNull(serializer);
        assertInstanceOf(DefaultCookieSerializer.class, serializer);
    }

    @Test
    void cookieSerializer_shouldConfigureCookieName() {
        DefaultCookieSerializer serializer = (DefaultCookieSerializer) securityConfig.cookieSerializer();
        String cookieName = (String) ReflectionTestUtils.getField(serializer, "cookieName");

        assertEquals("SESSION", cookieName);
    }

    @Test
    void cookieSerializer_shouldUseHttpOnlyCookie() {
        DefaultCookieSerializer serializer = (DefaultCookieSerializer) securityConfig.cookieSerializer();
        boolean useHttpOnlyCookie = (boolean) ReflectionTestUtils.getField(serializer, "useHttpOnlyCookie");

        assertTrue(useHttpOnlyCookie);
    }

    @Test
    void securityFilterChain_shouldConfigureChain() {
        HttpSecurity http = org.mockito.Mockito.mock(HttpSecurity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

        // Improve mock behavior if possible, but deep stubs might be enough to valid
        // "build" is called
        // However, since SecurityFilterChain is returned, we need to ensure
        // http.build() returns something.
        DefaultSecurityFilterChain chain = org.mockito.Mockito.mock(DefaultSecurityFilterChain.class);
        org.mockito.Mockito.when(http.build()).thenReturn(chain);

        SecurityFilterChain result = securityConfig.securityFilterChain(http);

        assertNotNull(result);
        assertEquals(chain, result);
    }

    @Test
    void securityFilterChain_shouldThrowBeanCreationException_whenExceptionOccurs() {
        HttpSecurity http = org.mockito.Mockito.mock(HttpSecurity.class);
        org.mockito.Mockito.when(http.csrf(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("Config error"));

        org.springframework.beans.factory.BeanCreationException exception = org.junit.jupiter.api.Assertions
                .assertThrows(
                        org.springframework.beans.factory.BeanCreationException.class,
                        () -> securityConfig.securityFilterChain(http));

        assertEquals("Security Filter Chain creation failed", exception.getMessage());
    }
}
