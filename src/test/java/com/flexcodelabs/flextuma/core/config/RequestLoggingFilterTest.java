package com.flexcodelabs.flextuma.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.io.IOException;
import java.security.Principal;

import com.flexcodelabs.flextuma.core.security.AuthenticatedUserCaptureFilter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpSession session;

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseRequestPrincipalWhenSecurityContextIsUnavailable() throws ServletException, IOException {
        Principal principal = mock(Principal.class);

        when(request.getRequestURI()).thenReturn("/api/me");
        when(request.getQueryString()).thenReturn(null);
        when(request.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("bennett");
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getUserPrincipal();
    }

    @Test
    void shouldUseCapturedUsernameAttributeBeforeOtherFallbacks() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/tags");
        when(request.getQueryString()).thenReturn(null);
        when(request.getAttribute(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            if (AuthenticatedUserCaptureFilter.REQUEST_USERNAME_ATTRIBUTE.equals(key)) {
                return "admin";
            }
            return null;
        });
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getAttribute(AuthenticatedUserCaptureFilter.REQUEST_USERNAME_ATTRIBUTE);
    }

    @Test
    void shouldUseSessionSecurityContextWhenPrincipalAndThreadContextAreUnavailable()
            throws ServletException, IOException {
        SecurityContext sessionContext = new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken("admin", null, java.util.List.of()));

        when(request.getRequestURI()).thenReturn("/api/lists");
        when(request.getQueryString()).thenReturn(null);
        when(request.getUserPrincipal()).thenReturn(null);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .thenReturn(sessionContext);
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getSession(false);
    }
}
