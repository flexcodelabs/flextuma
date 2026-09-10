package com.flexcodelabs.flextuma.core.exceptions;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${flextuma.app.frontend.directory:${APP_FRONTEND_DIRECTORY:/app/client}}")
    private String frontendDirectory;

    private static final Pattern UNIQUE_CONSTRAINT_PATTERN = Pattern
            .compile("Key \\(([^)]+)\\)=\\(([^)]+)\\) already exists");

    private static final Pattern NULL_COLUMN_PATTERN = Pattern.compile("column \"([^\"]+)\"");

    private static final Pattern MISSING_TABLE_ENTRY_PATTERN = Pattern.compile("is not present in table \"([^\"]+)\"");

    private static final Pattern VALUE_TOO_LONG_PATTERN = Pattern.compile("value too long for type (?:character varying|varchar)\\((\\d+)\\)");

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String defaultMessage = "The request body is missing or the JSON format is invalid.";
        if (ex.getMessage() != null && ex.getMessage().contains("Required request body is missing")) {
            defaultMessage = "Required request body is missing. Please provide the required data in the request body.";
        }

        Throwable mostSpecificCause = ex.getMostSpecificCause();
        String detailedMessage = mostSpecificCause.getMessage();

        String message = defaultMessage;

        if (detailedMessage != null && !detailedMessage.isBlank()
                && detailedMessage.contains("not one of the values accepted for Enum class")) {
            String enumMessage = tryBuildEnumErrorMessage(detailedMessage);
            if (enumMessage != null) {
                message = enumMessage;
            }
        }

        return buildResponse(message, HttpStatus.BAD_REQUEST, ex);
    }

    private String tryBuildEnumErrorMessage(String detailedMessage) {

        if (!detailedMessage.contains("not one of the values accepted for Enum class")) {
            return null;
        }

        String enumType = null;
        String invalidValue = null;
        String allowedValues = null;

        int typeStart = detailedMessage.indexOf("`");
        int typeEnd = detailedMessage.indexOf("`", typeStart + 1);
        if (typeStart != -1 && typeEnd != -1 && typeEnd > typeStart + 1) {
            enumType = detailedMessage.substring(typeStart + 1, typeEnd);
        }

        String fromStringToken = "from String \"";
        int valueStart = detailedMessage.indexOf(fromStringToken);
        if (valueStart != -1) {
            valueStart += fromStringToken.length();
            int valueEnd = detailedMessage.indexOf("\"", valueStart);
            if (valueEnd != -1 && valueEnd > valueStart) {
                invalidValue = detailedMessage.substring(valueStart, valueEnd);
            }
        }

        int valuesStart = detailedMessage.indexOf('[');
        int valuesEnd = detailedMessage.indexOf(']', valuesStart + 1);
        if (valuesStart != -1 && valuesEnd != -1 && valuesEnd > valuesStart + 1) {
            allowedValues = detailedMessage.substring(valuesStart + 1, valuesEnd);
        }

        if (allowedValues == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder("Invalid value");
        if (invalidValue != null) {
            builder.append(" '").append(invalidValue).append("'");
        }
        builder.append(" for enum");
        if (enumType != null) {
            int lastDot = enumType.lastIndexOf('.');
            String simpleName = lastDot != -1 ? enumType.substring(lastDot + 1) : enumType;
            builder.append(" ").append(simpleName);
        }
        builder.append(". Allowed values are: [").append(allowedValues).append("].");

        return builder.toString();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null)
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        return buildResponse(ex.getReason(), status, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage() != null ? violation.getMessage() : "Invalid constraint")
                .collect(Collectors.joining(", "));
        return buildResponse(message, HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDatabaseError(DataIntegrityViolationException ex) {
        Throwable rootCause = ex.getRootCause();
        String detail = (rootCause != null) ? rootCause.getMessage() : ex.getMessage();
        return buildResponse(sanitizeDatabaseError(detail), getResponseStatus(detail, HttpStatus.BAD_REQUEST), ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    String field = error.getField();
                    String defaultMsg = error.getDefaultMessage();
                    return (field != null ? field : "Field") + " " + (defaultMsg != null ? defaultMsg : "is invalid");
                })
                .collect(Collectors.joining(", "));
        return buildResponse(message, HttpStatus.BAD_REQUEST, ex);
    }

    // Service-layer validation throughout the app signals with these two (e.g. "Secret Key is
    // required for whatsapp", "You cannot delete an active connector") and expects the message
    // shown as-is. Without this handler they fall into handleGeneral() as a 500, and the
    // frontend's Axios interceptor discards any >=500 message and shows "Something went wrong"
    // instead -- so a perfectly good, specific message never reaches the user.
    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<Object> handleIllegalArgument(RuntimeException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        String message = ex.getMessage();
        if (message == null || message.equalsIgnoreCase("Access is denied")) {
            message = "You do not have permission to perform this action";
        }
        return buildResponse(message, HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
    public ResponseEntity<Object> handleNotFound(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String accept = request.getHeader("Accept");
        boolean acceptsHtml = accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
        if ("GET".equals(method) && acceptsHtml && !requestUri.startsWith("/api/")) {
            try {
                Path filePath = Paths.get(frontendDirectory, "index.html");
                Resource resource = new FileSystemResource(filePath.toString());

                if (resource.exists() && resource.isReadable()) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("text/html"))
                            .body(resource);
                }
            } catch (Exception e) {
                log.error("Error serving SPA index.html for URI: {}", requestUri, e);
            }
        }

        return buildResponse("Oops 😢! Route not available.", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Object> handleRateLimitExceeded(RateLimitExceededException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS, ex);
    }

    @ExceptionHandler(InvalidEnumValueException.class)
    public ResponseEntity<Object> handleEnumDeserializationError(InvalidEnumValueException ex) {
        String message = String.format("Invalid value provided for '%s'. Allowed values are: %s.",
                ex.getFieldName(), String.join(", ", ex.getEnumValues()));
        return buildResponse(message, HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(JsonMappingException.class)
    public ResponseEntity<Object> handleJsonMappingException(JsonMappingException ex) {
        if (ex.getCause() instanceof InvalidEnumValueException cause) {
            return handleEnumDeserializationError(cause);
        }
        return buildResponse("Invalid request format", HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Object> handleTransactionException(TransactionSystemException ex) {
        Throwable cause = ex.getRootCause();
        if (cause instanceof ConstraintViolationException constraintEx) {
            return handleConstraintViolationException(constraintEx);
        }
        if (cause instanceof DataIntegrityViolationException dataEx) {
            return handleDatabaseError(dataEx);
        }
        if (cause != null) {
            return buildResponse(sanitizeGeneralMessage(cause.getMessage()), HttpStatus.BAD_REQUEST, cause);
        }
        return buildResponse("Could not commit database transaction", HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Class<?> requiredType = ex.getRequiredType();
        String type = (requiredType != null) ? requiredType.getSimpleName() : "unknown";
        Object value = ex.getValue();
        String message = String.format("Parameter '%s' must be a valid %s. Received: '%s'", name, type, value);
        return buildResponse(message, HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneral(Exception ex) {
        return buildResponse(sanitizeGeneralMessage(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    private String sanitizeDatabaseError(String message) {
        if (message == null)
            return "Request failed";

        if (message.contains("unique constraint") || message.contains("Duplicate entry")
                || message.contains("Unique index")) {
            Matcher matcher = UNIQUE_CONSTRAINT_PATTERN.matcher(message);
            if (matcher.find()) {
                String fields = matcher.group(1).replace("_", " ");
                return fields + " already exists";
            }
            return "A record with these details already exists";
        }

        if (message.contains("null value in column")) {
            Matcher matcher = NULL_COLUMN_PATTERN.matcher(message);
            if (matcher.find())
                return matcher.group(1).replace("_", " ") + " cannot be null";
            return "Required field is missing";
        }

        if (message.contains("is not present in table")) {
            Matcher matcher = MISSING_TABLE_ENTRY_PATTERN.matcher(message);
            if (matcher.find())
                return matcher.group(1).replace("_", " ") + " could not be found";
        }

        if (message.contains("value too long")) {
            Matcher matcher = VALUE_TOO_LONG_PATTERN.matcher(message);
            if (matcher.find())
                return "Value exceeds the maximum length of " + matcher.group(1) + " characters";
            return "Value is too long";
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

    // 401/403/406 are routine, high-volume, and expected (unauthenticated requests, permission
    // checks, content-negotiation misses) -- logging them as errors would just be noise that
    // buries the failures worth investigating.
    private static final java.util.Set<HttpStatus> UNLOGGED_STATUSES = java.util.Set.of(
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_ACCEPTABLE);

    private ResponseEntity<Object> buildResponse(String message, HttpStatus status) {
        return buildResponse(message, status, null);
    }

    private ResponseEntity<Object> buildResponse(String message, HttpStatus status, Throwable ex) {
        HttpStatus finalStatus = getResponseStatus(message, status);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("error", finalStatus.getReasonPhrase());
        body.put("message", message != null ? capitalize(message) : "No message available");

        if (!UNLOGGED_STATUSES.contains(finalStatus)) {
            if (ex != null) {
                log.error("Request failed with {}: {}", finalStatus, message, ex);
            } else {
                log.error("Request failed with {}: {}", finalStatus, message);
            }
        }

        return new ResponseEntity<>(body, finalStatus);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;

        StringBuilder result = new StringBuilder();
        CapitalizeState state = new CapitalizeState();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            boolean shouldAppendDirectly = updateBracketState(c, state) ||
                    updateDotSpaceState(str, i, c, state);

            if (shouldAppendDirectly) {
                result.append(c);
            } else {
                appendProcessedChar(c, state, result);
            }
        }

        return result.toString().replace("_", " ");
    }

    private boolean updateBracketState(char c, CapitalizeState state) {
        if (c == '[') {
            state.insideBrackets = true;
        } else if (c == ']') {
            state.insideBrackets = false;
        }
        return state.insideBrackets;
    }

    private boolean updateDotSpaceState(String str, int i, char c, CapitalizeState state) {
        if (i > 0 && str.charAt(i - 1) == '.' && c == ' ') {
            state.previousWasDotSpace = true;
            return true;
        }
        return false;
    }

    private void appendProcessedChar(char c, CapitalizeState state, StringBuilder result) {
        if (!state.firstCharFound && Character.isLetterOrDigit(c)) {
            result.append(Character.toUpperCase(c));
            state.firstCharFound = true;
        } else if (state.previousWasDotSpace && Character.isLetter(c)) {
            result.append(Character.toUpperCase(c));
            state.previousWasDotSpace = false;
        } else {
            result.append(Character.toLowerCase(c));
            state.previousWasDotSpace = false;
        }
    }

    private static class CapitalizeState {
        boolean insideBrackets = false;
        boolean firstCharFound = false;
        boolean previousWasDotSpace = false;
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
