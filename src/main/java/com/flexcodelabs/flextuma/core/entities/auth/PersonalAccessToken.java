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

}
