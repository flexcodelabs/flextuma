package com.flexcodelabs.flextuma.core.services;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class SecurityLogService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityLogService.class);

    public void logLoginAttempt(String username, HttpServletRequest request, boolean success, String reason) {
        String clientInfo = getClientInfo(request);

        if (success) {
            if (logger.isInfoEnabled()) {
                logger.info("LOGIN_SUCCESS - User: {}, IP: {}, UserAgent: {}, Time: {}",
                        username, clientInfo, getUserAgent(request), LocalDateTime.now());
            }
        } else {
            if (logger.isWarnEnabled()) {
                logger.warn("LOGIN_FAILED - User: {}, IP: {}, UserAgent: {}, Reason: {}, Time: {}",
                        username, clientInfo, getUserAgent(request), reason, LocalDateTime.now());
            }
        }
    }

    public void logRegistrationAttempt(String username, String email, HttpServletRequest request, boolean success,
            String reason) {
        String clientInfo = getClientInfo(request);

        if (success) {
            if (logger.isInfoEnabled()) {
                logger.info("REGISTRATION_SUCCESS - User: {}, Email: {}, IP: {}, UserAgent: {}, Time: {}",
                        username, email, clientInfo, getUserAgent(request), LocalDateTime.now());
            }
        } else {
            if (logger.isWarnEnabled()) {
                logger.warn("REGISTRATION_FAILED - User: {}, Email: {}, IP: {}, UserAgent: {}, Reason: {}, Time: {}",
                        username, email, clientInfo, getUserAgent(request), reason, LocalDateTime.now());
            }
        }
    }

    public void logRateLimitExceeded(HttpServletRequest request, String endpoint) {
        if (logger.isWarnEnabled()) {
            String clientInfo = getClientInfo(request);
            String userAgent = getUserAgent(request);
            logger.warn("RATE_LIMIT_EXCEEDED - IP: {}, Endpoint: {}, UserAgent: {}, Time: {}",
                    clientInfo, endpoint, userAgent, LocalDateTime.now());
        }
    }

    public void logSuspiciousActivity(String activity, String details, HttpServletRequest request) {
        if (logger.isErrorEnabled()) {
            String clientInfo = getClientInfo(request);
            String userAgent = getUserAgent(request);
            logger.error("SUSPICIOUS_ACTIVITY - Activity: {}, Details: {}, IP: {}, UserAgent: {}, Time: {}",
                    activity, details, clientInfo, userAgent, LocalDateTime.now());
        }
    }

    public void logPasswordChange(String username, HttpServletRequest request, boolean success) {
        String clientInfo = getClientInfo(request);

        if (success) {
            if (logger.isInfoEnabled()) {
                logger.info("PASSWORD_CHANGE_SUCCESS - User: {}, IP: {}, UserAgent: {}, Time: {}",
                        username, clientInfo, getUserAgent(request), LocalDateTime.now());
            }
        } else {
            if (logger.isWarnEnabled()) {
                logger.warn("PASSWORD_CHANGE_FAILED - User: {}, IP: {}, UserAgent: {}, Time: {}",
                        username, clientInfo, getUserAgent(request), LocalDateTime.now());
            }
        }
    }

    public void logLogout(String username, HttpServletRequest request) {
        if (logger.isInfoEnabled()) {
            String clientInfo = getClientInfo(request);
            String userAgent = getUserAgent(request);
            logger.info("LOGOUT - User: {}, IP: {}, UserAgent: {}, Time: {}",
                    username, clientInfo, userAgent, LocalDateTime.now());
        }
    }

    private String getClientInfo(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }
}
