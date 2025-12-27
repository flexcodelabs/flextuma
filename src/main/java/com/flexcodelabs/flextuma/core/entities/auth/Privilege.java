package com.flexcodelabs.flextuma.core.entities.auth;

import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "privilege", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Privilege extends BaseEntity {

    public static final String PLURAL = "privileges";
    public static final String NAME_PLURAL = "Privileges";
    public static final String NAME_SINGULAR = "PrivilegeContoller";

    public static final String READ = "READ_PRIVILEGES";
    public static final String ADD = "ADD_PRIVILEGES";
    public static final String DELETE = "DELETE_PRIVILEGES";
    public static final String UPDATE = "UPDATE_PRIVILEGES";

    @Column(unique = true, nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String value;

    @Column(name = "system", nullable = false)
    private Boolean system = false;
}
