package com.flexcodelabs.flextuma.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    
    private int minLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigit;
    private boolean requireSpecialChar;
    private String specialChars;
    
    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.requireUppercase = constraintAnnotation.requireUppercase();
        this.requireLowercase = constraintAnnotation.requireLowercase();
        this.requireDigit = constraintAnnotation.requireDigit();
        this.requireSpecialChar = constraintAnnotation.requireSpecialChar();
        this.specialChars = constraintAnnotation.specialChars();
    }
    
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }
        
        // Check minimum length
        if (password.length() < minLength) {
            return false;
        }
        
        boolean hasUppercase = !requireUppercase || password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = !requireLowercase || password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = !requireDigit || password.chars().anyMatch(Character::isDigit);
        boolean hasSpecialChar = !requireSpecialChar || password.chars()
                .anyMatch(ch -> specialChars.indexOf(ch) >= 0);
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecialChar;
    }
}
