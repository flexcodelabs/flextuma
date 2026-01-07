package com.flexcodelabs.flextuma.core.exceptions;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDatabaseError(DataIntegrityViolationException ex) {
        String detail = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        return buildResponse(sanitizeDatabaseError(detail), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        String message = ex.getMessage();
        if (message == null || message.equalsIgnoreCase("Access is denied")) {
            message = "You do not have permission to perform this action";
        }
        return buildResponse(message, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
    public ResponseEntity<Object> handleNotFound() {
        return buildResponse("Oops 😢! Route not available.", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({ InvalidEnumValueException.class })
    public ResponseEntity<Object> handleEnumDeserializationError(InvalidEnumValueException ex) {
        String message = String.format("Invalid value provided for '%s'. Allowed values are: %s.",
                ex.getFieldName(), String.join(", ", ex.getEnumValues()));
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(JsonMappingException.class)
    public ResponseEntity<Object> handleJsonMappingException(JsonMappingException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidEnumValueException) {
            return handleEnumDeserializationError((InvalidEnumValueException) cause);
        }
        return buildResponse("Invalid request format", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
    public ResponseEntity<Object> handleTransactionException(
            org.springframework.transaction.TransactionSystemException ex) {
        System.out.println("TRANSACTION EXCEPTION: " + ex.getMessage());
        Throwable cause = ex.getCause();
        if (cause != null) {
            System.out.println("CAUSE: " + cause.getClass().getName() + ": " + cause.getMessage());
            Throwable rootCause = cause.getCause();
            if (rootCause != null) {
                System.out.println("ROOT CAUSE: " + rootCause.getClass().getName() + ": " + rootCause.getMessage());
                rootCause.printStackTrace();
            }
        }
        ex.printStackTrace();
        return buildResponse("Could not commit jpa transaction", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneral(Exception ex) {
        System.out.println("EXCEPTION: " + ex.getClass().getName() + ": " + ex.getMessage());
        if (ex.getCause() != null) {
            System.out.println("CAUSE: " + ex.getCause().getClass().getName() + ": " + ex.getCause().getMessage());
            if (ex.getCause().getCause() != null) {
                System.out.println("ROOT CAUSE: " + ex.getCause().getCause().getClass().getName() + ": "
                        + ex.getCause().getCause().getMessage());
            }
        }
        ex.printStackTrace();
        return buildResponse(sanitizeGeneralMessage(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String sanitizeDatabaseError(String message) {
        if (message == null)
            return "Request failed";

        if (message.contains("duplicate key value violates unique constraint")) {
            Pattern pattern = Pattern.compile("Key \\((.*?)\\)=\\((.*?)\\) already exists");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find())
                return capitalize(matcher.group(1)) + " already exists";
            return "Resource already exists";
        }

        if (message.contains("null value in column")) {
            Pattern pattern = Pattern.compile("column \"(.*?)\"");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find())
                return capitalize(matcher.group(1)) + " cannot be null";
            return "Field cannot be null";
        }

        if (message.contains("is not present in table")) {
            return "Referenced resource could not be found";
        }

        if (message.contains("data and salt arguments required"))
            return "Password is required";
        if (message.contains("Internal Server Error(DB)"))
            return "Internal Server Error(DB)";

        return "Database operation failed";
    }

    private String sanitizeGeneralMessage(String message) {
        if (message == null)
            return "Internal server error";
        if (message.contains("Could not find any entity"))
            return "Resource could not be found";
        if (message.contains("no such file"))
            return "Asset missing";
        return message;
    }

    private ResponseEntity<Object> buildResponse(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", capitalize(message));
        return new ResponseEntity<>(body, status);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
