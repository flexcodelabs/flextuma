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

    public SecurityConfig(CustomSecurityExceptionHandler securityExceptionHandler,
            PatAuthenticationFilter patAuthenticationFilter) {
        this.securityExceptionHandler = securityExceptionHandler;
        this.patAuthenticationFilter = patAuthenticationFilter;
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
                            .requestMatchers("/**").permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .addFilterBefore(patAuthenticationFilter,
                            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
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