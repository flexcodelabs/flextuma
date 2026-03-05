package com.flexcodelabs.flextuma.core.security;

import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
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

    private final PersonalAccessTokenRepository patRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey != null && !apiKey.isBlank()) {
            String hashedToken = hashToken(apiKey);
            Optional<PersonalAccessToken> patOpt = patRepository.findByToken(hashedToken);

            if (patOpt.isPresent()) {
                PersonalAccessToken pat = patOpt.get();

                if (pat.getExpiresAt() == null || pat.getExpiresAt().isAfter(LocalDateTime.now())) {
                    User user = pat.getUser();

                    Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                            .flatMap(role -> role.getPrivileges().stream())
                            .map(privilege -> new SimpleGrantedAuthority(privilege.getValue()))
                            .collect(Collectors.toSet());

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    pat.setLastUsedAt(LocalDateTime.now());
                    patRepository.save(pat);
                }
            }
        }

        filterChain.doFilter(request, response);
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
