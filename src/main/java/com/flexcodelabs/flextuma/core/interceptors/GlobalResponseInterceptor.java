package com.flexcodelabs.flextuma.core.interceptors;

import java.util.Map;
import com.flexcodelabs.flextuma.core.dto.ApiResponse;
import com.flexcodelabs.flextuma.core.dtos.UserResponseDto;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
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
        if (body == null) {
            return null;
        }

        if (body instanceof User user) {
            return UserResponseDto.fromUser(user);
        }

        if (body instanceof Owner owner) {
            return handleOwnerEntity(owner);
        }

        if (body instanceof Iterable<?> collection) {
            return handleCollection(collection);
        }

        if (body instanceof java.util.Map<?, ?> map) {
            return handleMap(map);
        }

        if (body instanceof ApiResponse<?> apiResponse) {
            return processApiResponse(apiResponse);
        }

        return body;
    }

    private Object handleCollection(Iterable<?> collection) {
        java.util.List<Object> convertedList = new java.util.ArrayList<>();
        boolean hasConversion = false;

        for (Object item : collection) {
            Object converted = convertEntitiesToDtos(item);
            convertedList.add(converted);
            if (converted != item) {
                hasConversion = true;
            }
        }

        return hasConversion ? convertedList : collection;
    }

    private Object handleMap(java.util.Map<?, ?> map) {
        java.util.Map<Object, Object> convertedMap = new java.util.HashMap<>();
        boolean hasConversion = false;

        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            Object convertedValue = convertEntitiesToDtos(entry.getValue());
            convertedMap.put(entry.getKey(), convertedValue);
            if (convertedValue != entry.getValue()) {
                hasConversion = true;
            }
        }

        return hasConversion ? convertedMap : map;
    }

    private static final String CREATED_BY_FIELD = "createdBy";
    private static final String UPDATED_BY_FIELD = "updatedBy";

    private Object handleOwnerEntity(Owner owner) {
        try {
            Map<String, Object> cleanEntity = new java.util.HashMap<>();

            java.lang.reflect.Field[] fields = owner.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();

                if (!CREATED_BY_FIELD.equals(fieldName) && !UPDATED_BY_FIELD.equals(fieldName)) {
                    Object value = field.get(owner);
                    if (value != null) {
                        cleanEntity.put(fieldName, value);
                    }
                }
            }

            fields = owner.getClass().getSuperclass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();

                if (CREATED_BY_FIELD.equals(fieldName)) {
                    User createdBy = (User) field.get(owner);
                    if (createdBy != null) {
                        cleanEntity.put(fieldName, UserResponseDto.fromUser(createdBy));
                    }
                } else if (UPDATED_BY_FIELD.equals(fieldName)) {
                    User updatedBy = (User) field.get(owner);
                    if (updatedBy != null) {
                        cleanEntity.put(fieldName, UserResponseDto.fromUser(updatedBy));
                    }
                } else {
                    Object value = field.get(owner);
                    if (value != null) {
                        cleanEntity.put(fieldName, value);
                    }
                }
            }

            fields = owner.getClass().getSuperclass().getSuperclass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null) {
                    cleanEntity.put(field.getName(), value);
                }
            }

            return cleanEntity;
        } catch (java.lang.IllegalAccessException e) {
            log.warn("Failed to handle Owner entity: {}", e.getMessage());
            return owner;
        }
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
