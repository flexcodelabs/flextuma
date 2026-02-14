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

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http
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
            throw new BeanCreationException("Security Filter Chain creation failed", e);
        }
    }
}