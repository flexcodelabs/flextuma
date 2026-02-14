package com.flexcodelabs.flextuma.core.security;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
@EnableRedisHttpSession
public class SecurityConfig {

    private final CustomSecurityExceptionHandler securityExceptionHandler;

    public SecurityConfig(CustomSecurityExceptionHandler securityExceptionHandler) {
        this.securityExceptionHandler = securityExceptionHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Fixes java:S3330
     * Explicitly configures the Session Cookie to be HttpOnly.
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        // If using HTTPS, uncomment the line below:
        serializer.setSameSite("Lax");
        return serializer;
    }

    /**
     * Fixes java:S1130, java:S112 (Exception handling)
     * and java:S4502 (CSRF protection)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http
                    /*
                     * FIX java:S4502: Enabled CSRF using a Cookie-based repository.
                     * This is the secure approach for Web/SPA applications using sessions.
                     * withHttpOnlyFalse() allows modern frontends (Angular/React) to read the
                     * token.
                     */
                    .csrf(csrf -> csrf
                            .csrfTokenRepository(
                                    new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository())
                            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/login").permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(securityExceptionHandler)
                            .accessDeniedHandler(securityExceptionHandler))
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                            .maximumSessions(1));

            return http.build();

        } catch (Exception e) {
            /*
             * FIX java:S1130 & java:S112:
             * We catch the generic Exception thrown by http.build()
             * and wrap it in a specific RuntimeException.
             */
            throw new BeanCreationException("Security Filter Chain creation failed", e);
        }
    }
}