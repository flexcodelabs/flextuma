package com.flexcodelabs.flextuma.core.entities.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Entity
@Table(name = "personalaccesstoken")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonalAccessToken extends BaseEntity {

    public static final String PLURAL = "tokens";
    public static final String NAME_PLURAL = "Personal Access Tokens";
    public static final String NAME_SINGULAR = "Personal Access Token";

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime lastUsedAt;

    private LocalDateTime expiresAt;

    @Transient
    private String rawToken;

    @PrePersist
    public void generateToken() {
        if (this.token == null) {
            this.rawToken = "ft_" + java.util.UUID.randomUUID().toString().replace("-", "");
            this.token = hashToken(this.rawToken);
        }
        if (this.getActive() == null) {
            this.setActive(true);
        }
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
