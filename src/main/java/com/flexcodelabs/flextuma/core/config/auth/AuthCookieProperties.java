package com.flexcodelabs.flextuma.core.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "auth.cookie")
@Getter
@Setter
public class AuthCookieProperties {
    private String name = "SESSION";
    private long maxAge = 3600;
    private boolean secure = true;
    private String sameSite = "Lax";
    private String path = "/";
}