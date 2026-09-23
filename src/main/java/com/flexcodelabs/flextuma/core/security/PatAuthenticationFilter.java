package com.flexcodelabs.flextuma.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.dto.ErrorResponse;
import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;
import com.flexcodelabs.flextuma.core.services.AuthRateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PatAuthenticationFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_SCOPE = "PAT";

    private final PersonalAccessTokenRepository patRepository;
    private final AuthRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey != null && !apiKey.isBlank()) {
            if (rateLimitService.isBlocked(request, RATE_LIMIT_SCOPE)) {
                writeTooManyRequests(response,
                        rateLimitService.getBlockTimeRemainingSeconds(request, RATE_LIMIT_SCOPE));
                return;
            }

            String hashedToken = hashToken(apiKey);
            Optional<PersonalAccessToken> patOpt = patRepository.findByToken(hashedToken);

            boolean authenticated = false;

            if (patOpt.isPresent()) {
                PersonalAccessToken pat = patOpt.get();

                if (Boolean.TRUE.equals(pat.getActive())
                        && (pat.getExpiresAt() == null || pat.getExpiresAt().isAfter(LocalDateTime.now()))) {
                    User user = pat.getUser();

                    Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                            .flatMap(role -> role.getPrivileges().stream())
                            .map(privilege -> new SimpleGrantedAuthority(privilege.getValue()))
                            .collect(Collectors.toSet());

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    if (pat.getScopes() != null) {
                        ApiTokenContext.set(new ApiTokenContext.TokenGrant(pat.getScopes(), pat.getAllowedConnectorIds(), Boolean.TRUE.equals(pat.getAllowSystemConnectors())));
                    }

                    pat.setLastUsedAt(LocalDateTime.now());
                    patRepository.save(pat);
                    authenticated = true;
                }
            }

            if (authenticated) {
                rateLimitService.recordSuccessfulAttempt(request, RATE_LIMIT_SCOPE);
            } else {
                rateLimitService.recordFailedAttempt(request, RATE_LIMIT_SCOPE);
            }
        }

        try { filterChain.doFilter(request, response); } finally { ApiTokenContext.clear(); }
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.tooManyRequests(
                "Too many invalid API key attempts. Try again in " + retryAfterSeconds + " seconds.");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 algorithm not found", e);
        }
    }
}
