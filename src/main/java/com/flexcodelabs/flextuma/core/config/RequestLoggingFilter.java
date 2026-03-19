package com.flexcodelabs.flextuma.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("FLEXTUMA");
    private static final String USERNAME_KEY = "username";

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUri = queryString != null ? requestUri + "?" + queryString : requestUri;

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            logRequest(request, response, fullUri, startTime, 500, ex);
            throw ex;
        } finally {
            // Only log in finally if we haven't already logged via the catch block
            // OR we rely on the response status. Best is to extract the logging logic
            // into a helper method.
            if (request.getAttribute("REQUEST_LOGGED") == null) {
                logRequest(request, response, fullUri, startTime, response.getStatus(), null);
            }
        }
    }

    private boolean shouldSkipLogging(String fullUri) {
        return fullUri.equals("/") || fullUri.contains("/actuator/") || fullUri.contains(".js")
                || fullUri.contains(".css")
                || fullUri.contains(".png") || fullUri.contains(".jpg") || fullUri.contains(".jpeg")
                || fullUri.contains(".gif") || fullUri.contains(".svg") || fullUri.contains(".ico")
                || fullUri.contains(".json") || fullUri.contains(".html") || fullUri.contains(".woff2")
                || fullUri.contains(".woff") || fullUri.contains(".ttf");
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, String fullUri, long startTime,
            int statusOverride, Exception ex) {
        if (shouldSkipLogging(fullUri)) {
            return;
        }
        request.setAttribute("REQUEST_LOGGED", true);
        String username = getUsername();
        long duration = System.currentTimeMillis() - startTime;

        int status = statusOverride > 0 ? statusOverride : response.getStatus();
        boolean isError = status >= 400 || ex != null;

        String logColor = isError ? "\u001B[31m" : "\u001B[32m";
        String reset = "\u001B[0m";

        String statusLog = logColor + (isError ? "ERROR" : "LOG") + reset;
        String userInfo = "\u001B[33m[" + username + "]\u001B[0m";
        String coloredMethod = logColor + request.getMethod() + reset;
        String coloredUri = logColor + fullUri + reset;

        org.slf4j.MDC.put(USERNAME_KEY, username);
        try {
            if (isError) {
                log.error("{} {} {} {} {}ms - Status: {}", statusLog, userInfo, coloredMethod, coloredUri, duration,
                        status);
            } else {
                log.info("{} {} {} {} {}ms - Status: {}", statusLog, userInfo, coloredMethod, coloredUri, duration,
                        status);
            }
        } finally {
            org.slf4j.MDC.remove(USERNAME_KEY);
        }
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Debug logging to understand the authentication context
        if (auth != null) {
            log.debug("Auth class: {}", auth.getClass().getSimpleName());
            log.debug("Auth authenticated: {}", auth.isAuthenticated());
            log.debug("Auth principal: {}", auth.getPrincipal());
            log.debug("Auth name: {}", auth.getName());
            log.debug("Auth details: {}", auth.getDetails());
        } else {
            log.debug("Authentication is null");
        }

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            // Don't return "SYSTEM" for actual authenticated users
            if (username != null && !username.trim().isEmpty() && !"SYSTEM".equalsIgnoreCase(username)) {
                return username;
            }
        }

        // For login requests, try to extract username from request parameters
        HttpServletRequest request = getCurrentRequest();
        if (request != null && request.getRequestURI().contains("/login")) {
            String loginUsername = request.getParameter(USERNAME_KEY);
            if (loginUsername != null && !loginUsername.trim().isEmpty()) {
                return loginUsername;
            }
        }

        return "SYSTEM";
    }

    private HttpServletRequest getCurrentRequest() {
        // Try to get current request from RequestContextHolder
        try {
            org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder
                    .getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletRequestAttributes) {
                return servletRequestAttributes.getRequest();
            }
        } catch (Exception e) {
            // Ignore - can't get current request
        }
        return null;
    }
}