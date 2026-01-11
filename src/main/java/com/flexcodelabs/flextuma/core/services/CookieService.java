package com.flexcodelabs.flextuma.core.services;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.config.auth.AuthCookieProperties;
import com.flexcodelabs.flextuma.core.helpers.TokenGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CookieService {

    private final AuthCookieProperties properties;

    public ResponseCookie createAuthCookie() {

        String token = TokenGenerator.generateSecureToken(32);

        return ResponseCookie.from(properties.getName(), token)
                .httpOnly(true)
                .secure(properties.isSecure())
                .path(properties.getPath())
                .maxAge(properties.getMaxAge())
                .sameSite(properties.getSameSite())
                .build();
    }

    public ResponseCookie deleteAuthCookie() {
        return ResponseCookie.from(properties.getName(), "")
                .httpOnly(true)
                .secure(properties.isSecure())
                .path(properties.getPath())
                .maxAge(0)
                .build();
    }
}