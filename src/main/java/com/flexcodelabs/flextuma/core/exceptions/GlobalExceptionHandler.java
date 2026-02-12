package com.flexcodelabs.flextuma.core.exceptions;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "The request body is missing or the JSON format is invalid.";
        if (ex.getMessage() != null && ex.getMessage().contains("Required request body is missing")) {
            message = "Required request body is missing. Please provide the required data in the request body.";
        }
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        return buildResponse(ex.getReason(), (HttpStatus) ex.getStatusCode());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDatabaseError(DataIntegrityViolationException ex) {
        Throwable rootCause = ex.getRootCause();
        String detail = (rootCause != null) ? rootCause.getMessage() : ex.getMessage();
        return buildResponse(sanitizeDatabaseError(detail), HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(InvalidEnumValueException.class)
    public ResponseEntity<Object> handleEnumDeserializationError(InvalidEnumValueException ex) {
        String message = String.format("Invalid value provided for '%s'. Allowed values are: %s.",
                ex.getFieldName(), String.join(", ", ex.getEnumValues()));
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(JsonMappingException.class)
    public ResponseEntity<Object> handleJsonMappingException(JsonMappingException ex) {
        if (ex.getCause() instanceof InvalidEnumValueException cause) {
            return handleEnumDeserializationError(cause);
        }
        return buildResponse("Invalid request format", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Object> handleTransactionException(TransactionSystemException ex) {
        Throwable cause = ex.getRootCause();
        if (cause instanceof ConstraintViolationException constraintEx) {
            return handleConstraintViolationException(constraintEx);
        }
        if (cause != null) {
            return buildResponse(sanitizeGeneralMessage(cause.getMessage()), HttpStatus.BAD_REQUEST);
        }
        return buildResponse("Could not commit database transaction", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        Object value = ex.getValue();
        String message = String.format("Parameter '%s' must be a valid %s. Received: '%s'", name, type, value);
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneral(Exception ex) {
        return buildResponse(sanitizeGeneralMessage(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String sanitizeDatabaseError(String message) {
        if (message == null)
            return "Request failed";

        if (message.contains("unique constraint") || message.contains("Duplicate entry")
                || message.contains("Unique index")) {
            Pattern pattern = Pattern.compile("Key \\((.*?)\\)=\\((.*?)\\) already exists");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String fields = matcher.group(1).replace("_", " ");
                return fields + " combination already exists";
            }
            return "A record with these details already exists";
        }

        if (message.contains("null value in column")) {
            Pattern pattern = Pattern.compile("column \"(.*?)\"");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find())
                return matcher.group(1).replace("_", " ") + " cannot be null";
            return "Required field is missing";
        }

        if (message.contains("is not present in table")) {
            Pattern pattern = Pattern.compile("is not present in table \"(.*?)\"");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find())
                return matcher.group(1).replace("_", " ") + " could not be found";
        }

        return "Database integrity violation";
    }

    private String sanitizeGeneralMessage(String message) {
        if (message == null)
            return "Internal server error";
        if (message.contains("Could not find any entity"))
            return "Resource could not be found";
        if (message.contains("no such file"))
            return "Asset is missing";
        return message;
    }

    private ResponseEntity<Object> buildResponse(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("error", status.getReasonPhrase());
        body.put("message", message != null ? capitalize(message) : "No message available");

        return new ResponseEntity<>(body, getResponseStatus(message, status));
    }

    /**
     * Capitalizes the first letter, lowercases the rest,
     * but IGNORES anything inside [square brackets].
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;

        StringBuilder result = new StringBuilder();
        boolean insideBrackets = false;
        boolean firstCharFound = false;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (c == '[')
                insideBrackets = true;

            if (insideBrackets) {
                result.append(c);
            } else {
                if (!firstCharFound && Character.isLetterOrDigit(c)) {
                    result.append(Character.toUpperCase(c));
                    firstCharFound = true;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }

            if (c == ']')
                insideBrackets = false;
        }

        return result.toString().replace("_", " ");
    }

    private HttpStatus getResponseStatus(String message, HttpStatus defaultStatus) {
        if (message == null)
            return defaultStatus;
        String lower = message.toLowerCase();
        if (lower.contains("cannot be null") || lower.contains("invalid") || lower.contains("missing")
                || lower.contains("required")) {
            return HttpStatus.BAD_REQUEST;
        }
        if (lower.contains("already exists"))
            return HttpStatus.CONFLICT;
        if (lower.contains("permission"))
            return HttpStatus.FORBIDDEN;
        if (lower.contains("not found") || lower.contains("could not be found"))
            return HttpStatus.NOT_FOUND;
        return defaultStatus;
    }
}