package com.flexcodelabs.flextuma.core.entities.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import com.fasterxml.jackson.annotation.JsonFilter;
import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@JsonFilter("CustomFilter")
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    private LocalDateTime created = LocalDateTime.now();

    @LastModifiedDate
    private LocalDateTime updated = LocalDateTime.now();

    private Boolean active = true;

    @Column(nullable = true, unique = true)
    private String code;

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }
}