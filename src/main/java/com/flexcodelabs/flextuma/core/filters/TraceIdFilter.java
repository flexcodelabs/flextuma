package com.flexcodelabs.flextuma.core.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    static final String TRACE_ID_KEY = "traceId";
    static final String USERNAME_KEY = "username";
    static final String TRACE_HEADER = "X-Trace-Id";

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TRACE_SUFFIX_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = generateTraceId();
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        String username = resolveUsername();
        if (username != null) {
            MDC.put(USERNAME_KEY, username);
        }

        try {
            filterChain.doFilter(request, response);

            if (username == null) {
                String resolvedAfterChain = resolveUsername();
                if (resolvedAfterChain != null) {
                    MDC.put(USERNAME_KEY, resolvedAfterChain);
                }
            }
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(USERNAME_KEY);
        }
    }

    String generateTraceId() {
        StringBuilder sb = new StringBuilder("tr_");
        for (int i = 0; i < TRACE_SUFFIX_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }
}
