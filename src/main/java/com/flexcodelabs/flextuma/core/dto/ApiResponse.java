package com.flexcodelabs.flextuma.core.dto;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorResponse error;
    private String message;

    private ApiResponse(boolean success, T data, ErrorResponse error, String message) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, null, message);
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, null);
    }

    public static <T> ApiResponse<T> error(ErrorResponse error, String message) {
        return new ApiResponse<>(false, null, error, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ErrorResponse getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setData(T data) {
        this.data = data;
    }

    public void setError(ErrorResponse error) {
        this.error = error;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
