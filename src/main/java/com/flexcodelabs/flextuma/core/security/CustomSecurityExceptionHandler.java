package com.flexcodelabs.flextuma.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Please provide valid credentials");
    }

    @Override
public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) 
        throws IOException {
    String message = accessDeniedException.getMessage();
    if (message == null) {
        message = "Access Denied: You do not have permission to perform this action";
    }
    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, message);
}

    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("status", status);
        data.put("message", message);
        data.put("error", message);

        response.getOutputStream().println(objectMapper.writeValueAsString(data));
    }
}