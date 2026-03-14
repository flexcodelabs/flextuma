package com.flexcodelabs.flextuma.core.dtos;

import jakarta.validation.constraints.NotBlank;

public class VerificationRequestDto {
    
    @NotBlank(message = "Identifier cannot be empty")
    private String identifier; // email or phone number
    
    @NotBlank(message = "Code cannot be empty")
    private String code;
    
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
}
