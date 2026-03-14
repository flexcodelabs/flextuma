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

        return ResponseCookie.from(properties.name(), token)
                .httpOnly(properties.httpOnly())
                .secure(properties.secure())
                .path(properties.path())
                .maxAge(properties.maxAge())
                .sameSite(properties.sameSite())
                .build();
    }

    public ResponseCookie deleteAuthCookie() {
        return ResponseCookie.from(properties.name(), "")
                .httpOnly(properties.httpOnly())
                .secure(properties.secure())
                .path(properties.path())
                .maxAge(0)
                .sameSite(properties.sameSite())
                .build();
    }
}