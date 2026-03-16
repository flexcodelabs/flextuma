package com.flexcodelabs.flextuma.core.interceptors;

import com.flexcodelabs.flextuma.core.dto.ApiResponse;
import com.flexcodelabs.flextuma.core.dtos.UserResponseDto;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Slf4j
@ControllerAdvice
public class GlobalResponseInterceptor implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {

        try {
            body = convertEntitiesToDtos(body);
            return body;
        } catch (Exception e) {
            log.error("GlobalResponseInterceptor error: {}", e.getMessage(), e);
            return body;
        }
    }

    private Object convertEntitiesToDtos(Object body) {
        if (body instanceof User user) {
            return UserResponseDto.fromUser(user);
        }

        if (body instanceof ApiResponse<?> apiResponse) {
            return processApiResponse(apiResponse);
        }

        return body;
    }

    private Object processApiResponse(ApiResponse<?> apiResponse) {
        try {
            Object dataField = apiResponse.getData();
            if (dataField instanceof User user) {
                UserResponseDto userDto = UserResponseDto.fromUser(user);
                // Since ApiResponse is generic, we need to create a new response with the
                // converted data
                return ApiResponse.success(userDto, apiResponse.getMessage());
            }
        } catch (Exception e) {
            log.warn("ApiResponse processing failed: {}", e.getMessage());
        }
        return apiResponse;
    }
}
