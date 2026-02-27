package com.flexcodelabs.flextuma.core.entities.auth;

import com.flexcodelabs.flextuma.core.entities.base.NameEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organisation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Organisation extends NameEntity {

    public static final String PLURAL = "organisations";
    public static final String NAME_PLURAL = "Organisations";
    public static final String NAME_SINGULAR = "Organisation";
    public static final String READ = "READ_ORGANISATIONS";
    public static final String ADD = "ADD_ORGANISATIONS";
    public static final String DELETE = "DELETE_ORGANISATIONS";
    public static final String UPDATE = "UPDATE_ORGANISATIONS";

    @NotBlank(message = "Phone number is required")
    @Column(name = "phonenumber", nullable = false)
    private String phoneNumber;

    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    private String address;

    @Column(nullable = true)
    private String website;
}
