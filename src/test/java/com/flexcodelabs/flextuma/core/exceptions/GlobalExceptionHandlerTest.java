package com.flexcodelabs.flextuma.core.exceptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @RestController
    static class TestController {
        @GetMapping("/test/response-status")
        public void throwResponseStatus() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad request error");
        }

        @GetMapping("/test/general")
        public void throwGeneral() throws Exception {
            throw new Exception("General error");
        }

        @GetMapping("/test/access-denied")
        public void throwAccessDenied() {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        @GetMapping("/test/not-readable")
        public void throwNotReadable() {
            throw new HttpMessageNotReadableException("Invalid JSON", new org.springframework.http.HttpInputMessage() {
                @Override
                public java.io.InputStream getBody() throws java.io.IOException {
                    return null;
                }

                @Override
                public org.springframework.http.HttpHeaders getHeaders() {
                    return null;
                }
            });
        }

        @GetMapping("/test/constraint-violation")
        public void throwConstraintViolation() {
            throw new ConstraintViolationException("Constraint violation", java.util.Collections.emptySet());
        }

        @PostMapping("/test/validation")
        public void throwValidation(@Valid @RequestBody TestDto dto) {
            // Intentionally empty logic to test validation exception
        }

        @GetMapping("/test/data-integrity")
        public void throwDataIntegrityViolation() {
            throw new org.springframework.dao.DataIntegrityViolationException("Data integrity violation",
                    new RuntimeException("Duplicate entry 'test' for key 'unique_key'"));
        }

        @GetMapping("/test/invalid-enum")
        public void throwInvalidEnum() {
            throw new InvalidEnumValueException("status", TestEnum.class);
        }

        @GetMapping("/test/json-mapping")
        public void throwJsonMapping() throws com.fasterxml.jackson.databind.JsonMappingException {
            throw new com.fasterxml.jackson.databind.JsonMappingException(null, "JSON mapping error");
        }

        @GetMapping("/test/transaction-system")
        public void throwTransactionSystem() {
            throw new org.springframework.transaction.TransactionSystemException("Transaction error");
        }

        @GetMapping("/test/type-mismatch")
        public void throwTypeMismatch(@org.springframework.web.bind.annotation.RequestParam Integer id) {
            // Intentionally empty. Used to test MethodArgumentTypeMismatchException which
            // occurs during parameter binding.
        }

        @GetMapping("/test/no-resource")
        public void throwNoResourceFound() throws org.springframework.web.servlet.resource.NoResourceFoundException {
            throw org.mockito.Mockito.mock(org.springframework.web.servlet.resource.NoResourceFoundException.class);
        }
    }

    enum TestEnum {
        VALUE1, VALUE2
    }

    static class TestDto {
        @NotNull
        public String name;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(globalExceptionHandler)
                .build();
    }

    @Test
    void handleResponseStatusException_shouldReturnCorrectStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/response-status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bad request error"));
    }

    @Test
    void handleGeneralException_shouldReturnInternalServerError() throws Exception {
        mockMvc.perform(get("/test/general")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("General error"));
    }

    @Test
    void handleAccessDenied_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/test/access-denied")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void handleHttpMessageNotReadable_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/not-readable")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is missing or the json format is invalid."));
    }

    @Test
    void handleConstraintViolation_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/constraint-violation")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(""));
    }

    @Test
    void handleMethodArgumentNotValid_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void handleDataIntegrityViolation_shouldReturnConflict() throws Exception {
        mockMvc.perform(get("/test/data-integrity")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void handleInvalidEnumValueException_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/invalid-enum")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Invalid value provided for 'status'. allowed values are: value1, value2."));
    }

    @Test
    void handleJsonMappingException_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/json-mapping")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request format"));
    }

    @Test
    void handleTransactionSystemException_shouldReturnInternalServerError() throws Exception {
        mockMvc.perform(get("/test/transaction-system")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Could not commit database transaction"));
    }

    @Test
    void handleMethodArgumentTypeMismatch_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/type-mismatch")
                .param("id", "invalid-id")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value("Parameter 'id' must be a valid integer. received: 'invalid-id'"));
    }

    @Test
    void handleNoResourceFoundException_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/test/no-resource")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Oops 😢! route not available."));
    }

    @Test
    void handleHttpRequestMethodNotSupported_shouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/test/response-status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Oops 😢! route not available."));
    }

}
