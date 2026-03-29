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
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableRedisHttpSession
public class SecurityConfig {

    private final CustomSecurityExceptionHandler securityExceptionHandler;
    private final PatAuthenticationFilter patAuthenticationFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final AuthenticatedUserCaptureFilter authenticatedUserCaptureFilter;

    public SecurityConfig(CustomSecurityExceptionHandler securityExceptionHandler,
            PatAuthenticationFilter patAuthenticationFilter,
            PasswordChangeRequiredFilter passwordChangeRequiredFilter,
            AuthenticatedUserCaptureFilter authenticatedUserCaptureFilter) {
        this.securityExceptionHandler = securityExceptionHandler;
        this.patAuthenticationFilter = patAuthenticationFilter;
        this.passwordChangeRequiredFilter = passwordChangeRequiredFilter;
        this.authenticatedUserCaptureFilter = authenticatedUserCaptureFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/login").permitAll()
                            .requestMatchers("/api/register").permitAll()
                            .requestMatchers("/").permitAll()
                            .requestMatchers("/assets/**").permitAll()
                            .requestMatchers(new RegexRequestMatcher("^/(?!api(?:/|$)).*", null)).permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .addFilterBefore(patAuthenticationFilter,
                            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(passwordChangeRequiredFilter,
                            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(authenticatedUserCaptureFilter, PasswordChangeRequiredFilter.class)
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
