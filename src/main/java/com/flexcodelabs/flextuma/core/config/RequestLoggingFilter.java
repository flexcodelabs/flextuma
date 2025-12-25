package com.flexcodelabs.flextuma.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger; // Use this
import org.slf4j.LoggerFactory; // Use this
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// REMOVE @Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("FLEXTUMA");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUri = queryString != null ? requestUri + "?" + queryString : requestUri;

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            String username = getUsername();

            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            boolean isError = status >= 400;
            String logColor = isError ? "\u001B[31m" : "\u001B[32m";
            String reset = "\u001B[0m";

            String greenLog = logColor + (isError ? "ERROR" : "LOG") + reset;
            String userInfo = "\u001B[33m[" + username + "]\u001B[0m";
            String coloredMethod = logColor + request.getMethod() + reset;
            String coloredUri = logColor + fullUri + reset;

            log.info("{} {} {} {} {}ms", greenLog, userInfo, coloredMethod, coloredUri, duration);
        }
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}