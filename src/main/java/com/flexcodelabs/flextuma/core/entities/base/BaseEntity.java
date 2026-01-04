package com.flexcodelabs.flextuma.core.entities.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.fasterxml.jackson.annotation.JsonFilter;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@JsonFilter("CustomFilter")
public abstract class BaseEntity {

    @Id
    private UUID id;

    @CreatedDate
    private LocalDateTime created = LocalDateTime.now();

    @LastModifiedDate
    private LocalDateTime updated = LocalDateTime.now();

    private Boolean active = true;

    @Column(nullable = true)
    private String code;
}