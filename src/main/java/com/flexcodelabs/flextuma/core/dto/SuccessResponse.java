package com.flexcodelabs.flextuma.core.dto;

public class SuccessResponse {
    private String message;
    
    public SuccessResponse() {}
    
    public SuccessResponse(String message) {
        this.message = message;
    }
    
    public static SuccessResponse of(String message) {
        return new SuccessResponse(message);
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
