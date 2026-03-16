package com.flexcodelabs.flextuma.core.dtos;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.flexcodelabs.flextuma.core.entities.auth.Role;

public class UserResponseDto {
    private UUID id;
    private String name;
    private String username;
    private String phoneNumber;
    private String email;
    private LocalDateTime lastLogin;
    private Boolean verified;
    private Boolean active;
    private Boolean changePassword;
    private Set<String> roles;
    private LocalDateTime created;
    private LocalDateTime updated;

    public UserResponseDto() {
        // Default constructor for JSON deserialization
    }

    public static UserResponseDto fromUser(com.flexcodelabs.flextuma.core.entities.auth.User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setEmail(user.getEmail());
        dto.setLastLogin(user.getLastLogin());
        dto.setVerified(user.getVerified());
        dto.setActive(user.getActive());
        dto.setChangePassword(user.getChangePassword());
        dto.setCreated(user.getCreated());
        dto.setUpdated(user.getUpdated());

        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet()));
        }

        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getChangePassword() {
        return changePassword;
    }

    public void setChangePassword(Boolean changePassword) {
        this.changePassword = changePassword;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }
}
