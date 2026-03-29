package com.flexcodelabs.flextuma.core.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "flextuma.auth")
public record ApiKeyAuthProperties(
        @DefaultValue("/api/webhooks/**,/api/external/**") List<String> apiKeyEndpoints) {
}