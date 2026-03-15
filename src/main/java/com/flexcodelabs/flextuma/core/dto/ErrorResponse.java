package com.flexcodelabs.flextuma.core.dto;

import java.time.LocalDateTime;

public class ErrorResponse {
    private String message;
    private String errorCode;
    private int status;
    private LocalDateTime timestamp;
    
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(String message, String errorCode, int status) {
        this();
        this.message = message;
        this.errorCode = errorCode;
        this.status = status;
    }
    
    public static ErrorResponse of(String message, String errorCode, int status) {
        return new ErrorResponse(message, errorCode, status);
    }
    
    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(message, "BAD_REQUEST", 400);
    }
    
    public static ErrorResponse unauthorized(String message) {
        return new ErrorResponse(message, "UNAUTHORIZED", 401);
    }
    
    public static ErrorResponse forbidden(String message) {
        return new ErrorResponse(message, "FORBIDDEN", 403);
    }
    
    public static ErrorResponse notFound(String message) {
        return new ErrorResponse(message, "NOT_FOUND", 404);
    }
    
    public static ErrorResponse conflict(String message) {
        return new ErrorResponse(message, "CONFLICT", 409);
    }
    
    public static ErrorResponse tooManyRequests(String message) {
        return new ErrorResponse(message, "TOO_MANY_REQUESTS", 429);
    }
    
    public static ErrorResponse internalServerError(String message) {
        return new ErrorResponse(message, "INTERNAL_SERVER_ERROR", 500);
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
