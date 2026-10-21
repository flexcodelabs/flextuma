package com.flexcodelabs.flextuma.modules.whatsapp.services.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

/**
 * Minimal Graph API client for checking WhatsApp credentials. Only https://graph.facebook.com is
 * allowed: the base URL comes from user input, and the request carries their access token, so any
 * other host would turn "test connection" into a way to probe arbitrary URLs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetaGraphClient {
    static final String GRAPH_HOST = "graph.facebook.com";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Map<Integer, String> FRIENDLY_ERRORS = Map.of(
            190, "The access token is invalid or has expired. Use a permanent System User token from Meta Business Settings.",
            100, "Meta couldn't find that ID, or this access token can't access it. Check the ID and that the token's System User is assigned to the WhatsApp account.",
            10, "This access token is missing a permission. It needs whatsapp_business_management and whatsapp_business_messaging.",
            200, "This access token is missing a permission. It needs whatsapp_business_management and whatsapp_business_messaging.",
            4, "Meta is rate limiting requests for this app. Try again in a minute.",
            80007, "Meta is rate limiting requests for this WhatsApp account. Try again in a minute.");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> get(String baseUrl, String token, String path) {
        return call(HttpMethod.GET, baseUrl, token, path);
    }

    public Map<String, Object> post(String baseUrl, String token, String path) {
        return call(HttpMethod.POST, baseUrl, token, path);
    }

    public static String validatedBaseUrl(String baseUrl) {
        URI uri = parse(baseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !GRAPH_HOST.equalsIgnoreCase(uri.getHost())) {
            throw new MetaGraphException("INVALID_URL", "The Graph API URL must be https://graph.facebook.com/<version>.");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private static URI parse(String baseUrl) {
        try {
            return URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new MetaGraphException("INVALID_URL", "The Graph API URL is not a valid URL.");
        }
    }

    private Map<String, Object> call(HttpMethod method, String baseUrl, String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(validatedBaseUrl(baseUrl) + path, method, new HttpEntity<>(headers), Map.class);
            return objectMapper.convertValue(response.getBody() == null ? Map.of() : response.getBody(), MAP_TYPE);
        } catch (HttpStatusCodeException e) {
            throw translate(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.warn("Could not reach the Meta Graph API: {}", e.getMessage());
            throw new MetaGraphException("UNREACHABLE", "Could not reach Meta. Check your connection and try again.");
        }
    }

    MetaGraphException translate(String body) {
        Map<String, Object> error = errorOf(body);
        Object code = error.get("code");
        String fallback = error.get("message") instanceof String message ? message : "Meta rejected the request.";
        Integer numeric = code instanceof Number number ? number.intValue() : null;
        return new MetaGraphException(String.valueOf(code), FRIENDLY_ERRORS.getOrDefault(numeric, fallback));
    }

    private Map<String, Object> errorOf(String body) {
        try {
            Object error = objectMapper.readValue(body, MAP_TYPE).get("error");
            return error instanceof Map<?, ?> ? objectMapper.convertValue(error, MAP_TYPE) : Map.of();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Map.of();
        }
    }
}
