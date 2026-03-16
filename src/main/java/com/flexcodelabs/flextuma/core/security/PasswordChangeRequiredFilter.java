package com.flexcodelabs.flextuma.core.security;

import com.flexcodelabs.flextuma.core.dto.ErrorResponse;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.modules.auth.services.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (shouldSkipPasswordChangeCheck(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            try {
                User user = userService.findByUsername(auth.getName());

                if (Boolean.TRUE.equals(user.getChangePassword())) {
                    ErrorResponse errorResponse = new ErrorResponse(
                            "Password change required. Please change your password to continue.",
                            "PRECONDITION_REQUIRED", 428);

                    response.setStatus(428);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                    return;
                }
            } catch (Exception e) {
                log.warn("Error checking password change requirement for user {}: {}", auth.getName(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldSkipPasswordChangeCheck(String path) {
        return path.equals("/api/login") ||
                path.equals("/api/changePassword") ||
                path.equals("/api/logout") ||
                path.startsWith("/api/register") ||
                path.startsWith("/api/verify") ||
                path.startsWith("/api/resendVerification") ||
                path.startsWith("/assets/") ||
                path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.equals("/") ||
                path.endsWith(".html") ||
                path.endsWith(".css") ||
                path.endsWith(".js") ||
                path.endsWith(".png") ||
                path.endsWith(".jpg") ||
                path.endsWith(".jpeg") ||
                path.endsWith(".svg") ||
                path.endsWith(".ico");
    }
}
