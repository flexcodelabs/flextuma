package com.flexcodelabs.flextuma.core.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterUsernameTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private TraceIdFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        traceIdFilter = new TraceIdFilter();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void shouldSetUsernameInMDCWhenUserIsAuthenticated() throws ServletException, IOException {
        // Arrange
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "testuser", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Act - use a custom filter chain that checks MDC before cleanup
        FilterChain customFilterChain = new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                // Check MDC value during filter execution (before cleanup)
                String usernameDuringExecution = MDC.get("username");
                System.out.println("MDC username during execution: " + usernameDuringExecution);
                assertEquals("testuser", usernameDuringExecution);
            }
        };

        traceIdFilter.doFilterInternal(request, response, customFilterChain);

        // Assert - MDC should be cleaned up after filter execution
        assertNull(MDC.get("username"));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void shouldNotSetUsernameWhenUserIsAnonymous() throws ServletException, IOException {
        // Arrange - no authentication set

        // Act
        traceIdFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(MDC.get("username"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotSetUsernameWhenUserIsNotAuthenticated() throws ServletException, IOException {
        // Arrange
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "testuser", null, List.of());
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Act
        traceIdFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(MDC.get("username"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldCleanUpMDCAfterFilterChain() throws ServletException, IOException {
        // Arrange
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "testuser", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Act
        traceIdFilter.doFilterInternal(request, response, filterChain);

        // Assert - MDC should be cleaned up after filter execution
        assertNull(MDC.get("username"));
        assertNull(MDC.get("traceId"));
    }
}
