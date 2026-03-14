package com.flexcodelabs.flextuma.core.config.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(
        @NotBlank @DefaultValue("SESSION") String name,

        @NotNull @DefaultValue("3600s") Duration maxAge,

        @DefaultValue("true") boolean secure,

        @DefaultValue("true") boolean httpOnly,

        @NotBlank @DefaultValue("Strict") String sameSite,

        @NotBlank @DefaultValue("/") String path) {
}