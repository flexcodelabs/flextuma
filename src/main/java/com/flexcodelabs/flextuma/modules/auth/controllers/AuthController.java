package com.flexcodelabs.flextuma.modules.auth.controllers;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.dtos.LoginDto;
import com.flexcodelabs.flextuma.core.dtos.RegisterDto;
import com.flexcodelabs.flextuma.core.dtos.PasswordChangeDto;
import com.flexcodelabs.flextuma.core.dto.ApiResponse;
import com.flexcodelabs.flextuma.core.dtos.UserResponseDto;
import com.flexcodelabs.flextuma.core.dto.ErrorResponse;
import com.flexcodelabs.flextuma.core.dtos.VerificationRequestDto;
import com.flexcodelabs.flextuma.core.exceptions.RateLimitExceededException;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.services.AuthRateLimitService;
import com.flexcodelabs.flextuma.core.services.SecurityLogService;
import com.flexcodelabs.flextuma.core.services.VerificationService;
import com.flexcodelabs.flextuma.modules.auth.services.AuthenticationResult;
import com.flexcodelabs.flextuma.modules.auth.services.UserService;
import com.flexcodelabs.flextuma.modules.finance.services.WalletService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/")
@RequiredArgsConstructor
public class AuthController {

        private final UserService userService;
        private final WalletService walletService;
        private final AuthRateLimitService rateLimitService;
        private final SecurityLogService securityLogService;
        private final VerificationService verificationService;

        @Value("${flextuma.sms.price-per-segment:1.0}")
        private BigDecimal pricePerSegment;

        @Value("${flextuma.registration.bonus-segments:5}")
        private BigDecimal registrationBonusSegments;

        @PostMapping("/register")
        public ResponseEntity<Object> register(@Valid @RequestBody RegisterDto request,
                        HttpServletRequest httpRequest) {
                if (rateLimitService.isBlocked(httpRequest)) {
                        long remainingTime = rateLimitService.getBlockTimeRemainingSeconds(httpRequest);
                        throw new RateLimitExceededException("Too many registration attempts", remainingTime);
                }

                User user = userService.register(request);

                verificationService.generateVerificationCode(user.getEmail());
                verificationService.generateVerificationCode(user.getPhoneNumber());

                securityLogService.logRegistrationAttempt(user.getUsername(), user.getEmail(), httpRequest,
                                true, "Registration successful");

                walletService.credit(user, registrationBonusSegments, "Registration test SMS credits",
                                "REGISTRATION_BONUS");

                rateLimitService.recordSuccessfulAttempt(httpRequest);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(user);
        }

        @PostMapping("/login")
        public ResponseEntity<Object> login(
                        @Valid @RequestBody LoginDto request,
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {
                if (rateLimitService.isBlocked(httpRequest)) {
                        long remainingTime = rateLimitService.getBlockTimeRemainingSeconds(httpRequest);
                        throw new RateLimitExceededException("Too many login attempts", remainingTime);
                }

                AuthenticationResult authResult = userService.authenticateAndCreateContext(
                                request.getUsername(), request.getPassword(), httpRequest, httpResponse);

                rateLimitService.recordSuccessfulAttempt(httpRequest);
                securityLogService.logLoginAttempt(authResult.user().getUsername(), httpRequest, true,
                                "Login successful");

                return ResponseEntity.ok()
                                .body(authResult.user());
        }

        @PostMapping("/logout")
        public ResponseEntity<Object> logout(HttpServletRequest request, HttpServletResponse response) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                        securityLogService.logLogout(auth.getName(), request);
                }

                jakarta.servlet.http.HttpSession session = request.getSession(false);
                if (session != null) {
                        session.invalidate();
                }
                SecurityContextHolder.clearContext();

                jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("SESSION", "");
                cookie.setHttpOnly(true);
                cookie.setPath("/");
                cookie.setMaxAge(0);
                response.addCookie(cookie);

                return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        }

        @GetMapping("/me")
        public ResponseEntity<Object> me() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                if (auth == null ||
                                !auth.isAuthenticated() ||
                                auth instanceof AnonymousAuthenticationToken) {
                        return ResponseEntity.status(401)
                                        .body(ErrorResponse.unauthorized("Unauthorized"));
                }
                User user = userService.findByUsername(auth.getName());
                return ResponseEntity.ok()
                                .body(user);
        }

        @PostMapping("/verify")
        public ResponseEntity<Object> verify(@Valid @RequestBody VerificationRequestDto request,
                        HttpServletRequest httpRequest) {
                if (verificationService.verifyCode(request.getIdentifier(), request.getCode())) {
                        securityLogService.logRegistrationAttempt("", request.getIdentifier(), httpRequest,
                                        true, "Verification successful");
                        return ResponseEntity.ok()
                                        .body(ApiResponse.success("Verification successful"));
                } else {
                        securityLogService.logRegistrationAttempt("", request.getIdentifier(), httpRequest,
                                        false, "Invalid verification code");
                        return ResponseEntity.badRequest()
                                        .body(ErrorResponse
                                                        .badRequest("Invalid or expired verification code"));
                }
        }

        @PostMapping("/resendVerification")
        public ResponseEntity<ApiResponse<String>> resendVerification(@RequestBody VerificationRequestDto request,
                        HttpServletRequest httpRequest) {
                if (rateLimitService.isBlocked(httpRequest)) {
                        throw new RateLimitExceededException("Too many requests");
                }

                verificationService.resendCode(request.getIdentifier());
                securityLogService.logRegistrationAttempt("", request.getIdentifier(), httpRequest, true,
                                "Verification code resent");

                return ResponseEntity.ok()
                                .body(ApiResponse.success("Verification code sent"));
        }

        @PostMapping("/changePassword")
        public ResponseEntity<Object> changePassword(
                        @Valid @RequestBody PasswordChangeDto request,
                        HttpServletRequest httpRequest) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                        return ResponseEntity.status(401)
                                        .body(ErrorResponse.unauthorized("Unauthorized"));
                }

                User user = userService.findByUsername(auth.getName());

                if (!user.validatePassword(request.getCurrentPassword())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorResponse
                                                        .badRequest("Current password is incorrect"));
                }

                if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorResponse
                                                        .badRequest("New password and confirmation do not match"));
                }

                userService.changePassword(user, BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));

                securityLogService.logPasswordChange(user.getUsername(), httpRequest, true);

                return ResponseEntity.ok()
                                .body(ApiResponse.success(UserResponseDto.fromUser(user)));
        }
}
