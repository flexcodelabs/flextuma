package com.flexcodelabs.flextuma.core.interceptors;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@RestControllerAdvice
public class EntityAuditInterceptor extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, 
                            Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> clazz = ResolvableType.forType(targetType).resolve();
        return clazz != null && Owner.class.isAssignableFrom(clazz);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, 
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && body instanceof Owner entity) {
            Object principal = auth.getPrincipal();

            if (principal instanceof User user) {
                
                if (parameter.hasMethodAnnotation(PostMapping.class)) {
                    entity.setCreatedBy(user);
                }

                if (parameter.hasMethodAnnotation(PutMapping.class)) {
                    entity.setUpdatedBy(user);
                }
            }
        }

        return body;
    }
}