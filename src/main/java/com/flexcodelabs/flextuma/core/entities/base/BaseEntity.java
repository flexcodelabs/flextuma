package com.flexcodelabs.flextuma.core.entities.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private LocalDateTime created;

    @LastModifiedDate
    private LocalDateTime updated;

    private Boolean active = true;

    @Column(nullable = true, unique = true)
    private String code;

    // id is assigned by Hibernate itself during persist() (GenerationType.UUID is
    // client-side, before-execution generation), so it's genuinely null only for an
    // entity that has never been saved. Persistable.isNew() drives Spring Data's
    // save() choice between persist() (new) and merge() (existing, possibly detached
    // from a different transaction) -- a stale "always new" override here made every
    // load-then-save-in-a-later-transaction pattern call persist() on an already-row-
    // having entity, which Hibernate rejects as "Detached entity passed to persist".
    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNew() {
        return id == null;
    }
}