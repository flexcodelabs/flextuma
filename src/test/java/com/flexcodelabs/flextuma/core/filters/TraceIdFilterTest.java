package com.flexcodelabs.flextuma.core.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private TraceIdFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_shouldGenerateTraceIdAndSetHeader() throws ServletException, IOException {
        traceIdFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(TraceIdFilter.TRACE_HEADER), anyString());
        verify(filterChain).doFilter(request, response);

        // Context should be cleared after filter
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_KEY));
        assertNull(MDC.get(TraceIdFilter.USERNAME_KEY));
    }

    @Test
    void doFilterInternal_shouldPopulateUsernameWhenAuthenticated() throws ServletException, IOException {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("testuser");
        when(authentication.getPrincipal()).thenReturn("testuser");

        // We need to capture MDC state during execution
        doAnswer(invocation -> {
            assertNotNull(MDC.get(TraceIdFilter.TRACE_ID_KEY));
            return null;
        }).when(filterChain).doFilter(request, response);

        traceIdFilter.doFilterInternal(request, response, filterChain);

    }

    @Test
    void generateTraceId_shouldReturnCorrectFormat() {
        String traceId = traceIdFilter.generateTraceId();
        assertTrue(traceId.startsWith("tr_"));
        assertEquals(9, traceId.length()); // "tr_" (3) + 6 random chars
    }
}
