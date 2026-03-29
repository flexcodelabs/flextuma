package com.flexcodelabs.flextuma.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

import com.flexcodelabs.flextuma.core.security.AuthenticatedUserCaptureFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("FLEXTUMA");
    private static final String USERNAME = "username";
    private static final String SYSTEM = "SYSTEM";

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
        String username = getUsername(request);
        long duration = System.currentTimeMillis() - startTime;

        int status = statusOverride > 0 ? statusOverride : response.getStatus();
        boolean isError = status >= 400 || ex != null;

        String logColor = isError ? "\u001B[31m" : "\u001B[32m";
        String reset = "\u001B[0m";

        String statusLog = logColor + (isError ? "ERROR" : "LOG") + reset;
        String userInfo = "\u001B[33m[" + username + "]\u001B[0m";
        String coloredMethod = logColor + request.getMethod() + reset;
        String coloredUri = logColor + fullUri + reset;

        org.slf4j.MDC.put(USERNAME, username);
        try {
            if (isError) {
                log.error("{} {} {} {} {}ms - Status: {}", statusLog, userInfo, coloredMethod, coloredUri, duration,
                        status);
            } else {
                log.info("{} {} {} {} {}ms - Status: {}", statusLog, userInfo, coloredMethod, coloredUri, duration,
                        status);
            }
        } finally {
            org.slf4j.MDC.remove(USERNAME);
        }
    }

    private String getUsername(HttpServletRequest request) {
        String username = getCapturedUsername(request);
        if (username != null) {
            return username;
        }

        username = getPrincipalUsername(request);
        if (username != null) {
            return username;
        }

        username = getAuthenticationUsername();
        if (username != null) {
            return username;
        }

        username = getSessionUsername(request);
        if (username != null) {
            return username;
        }

        username = getLoginUsername(request);
        if (username != null) {
            return username;
        }

        log.debug("Returning SYSTEM as fallback");
        return SYSTEM;
    }

    private String getCapturedUsername(HttpServletRequest request) {
        Object capturedUsername = request.getAttribute(AuthenticatedUserCaptureFilter.REQUEST_USERNAME_ATTRIBUTE);
        if (capturedUsername instanceof String username
                && !username.trim().isEmpty()
                && !SYSTEM.equalsIgnoreCase(username)) {
            return username;
        }
        return null;
    }

    private String getPrincipalUsername(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().trim().isEmpty()) {
            return principal.getName();
        }
        return null;
    }

    private String getAuthenticationUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logAuthenticationDetails(auth);

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            if (username != null && !username.trim().isEmpty() && !SYSTEM.equalsIgnoreCase(username)) {
                log.debug("Returning username: {}", username);
                return username;
            }
        }
        return null;
    }

    private void logAuthenticationDetails(Authentication auth) {
        log.debug("Authentication found: {}", auth != null);
        if (auth != null) {
            log.debug("Auth class: {}", auth.getClass().getSimpleName());
            log.debug("Auth authenticated: {}", auth.isAuthenticated());
            log.debug("Auth principal: {}", auth.getPrincipal());
            log.debug("Auth name: {}", auth.getName());
            log.debug("Auth details: {}", auth.getDetails());
            log.debug("Auth authorities: {}", auth.getAuthorities());
        } else {
            log.debug("Authentication is null");
        }
    }

    private String getSessionUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object contextAttr = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (contextAttr instanceof SecurityContext securityContext) {
                Authentication sessionAuth = securityContext.getAuthentication();
                if (sessionAuth != null && sessionAuth.isAuthenticated()
                        && !"anonymousUser".equals(sessionAuth.getPrincipal())) {
                    String sessionUsername = sessionAuth.getName();
                    if (sessionUsername != null && !sessionUsername.trim().isEmpty()
                            && !SYSTEM.equalsIgnoreCase(sessionUsername)) {
                        return sessionUsername;
                    }
                }
            }
        }
        return null;
    }

    private String getLoginUsername(HttpServletRequest request) {
        if (request != null && request.getRequestURI().contains("/login")) {
            String loginUsername = request.getParameter(USERNAME);
            if (loginUsername != null && !loginUsername.trim().isEmpty()) {
                return loginUsername;
            }
        }
        return null;
    }
}
