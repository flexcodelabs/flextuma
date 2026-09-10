package com.flexcodelabs.flextuma.modules.auth.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.dtos.UsernameAvailabilityDto;
import com.flexcodelabs.flextuma.core.dtos.UsernameAvailabilityRequestDto;
import com.flexcodelabs.flextuma.core.services.PublicEndpointRateLimitService;
import com.flexcodelabs.flextuma.modules.auth.services.UserService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** Unauthenticated signup-time lookups -- see SecurityConfig's /api/public/** matcher. Being
 * unauthenticated and free of any per-user throttling elsewhere, every endpoint here must rate
 * limit itself against scraping/enumeration. */
@RestController
@RequestMapping("/api/public/users")
@RequiredArgsConstructor
public class PublicUserController {

    private static final String USERNAME_AVAILABILITY_BUCKET = "username-availability";

    private final UserService userService;
    private final PublicEndpointRateLimitService rateLimitService;

    @Value("${flextuma.rate-limit.username-availability.max-requests:20}")
    private int maxRequestsPerWindow;

    @Value("${flextuma.rate-limit.username-availability.window-seconds:60}")
    private int windowSeconds;

    @PostMapping("/username-availability")
    public ResponseEntity<UsernameAvailabilityDto> checkUsernameAvailability(
            @RequestBody UsernameAvailabilityRequestDto request, HttpServletRequest httpRequest) {
        rateLimitService.checkAndRecord(USERNAME_AVAILABILITY_BUCKET, httpRequest, maxRequestsPerWindow, windowSeconds);
        return ResponseEntity.ok(userService.checkUsernameAvailability(request.username(), request.email()));
    }
}
