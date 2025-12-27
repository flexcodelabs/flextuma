package com.flexcodelabs.flextuma.core.entities.base;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

public class Owner extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator", referencedColumnName = "id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updator", referencedColumnName = "id")
    private User updatedBy;
}
