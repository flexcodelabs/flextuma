package com.flexcodelabs.flextuma.core.entities.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public class NameEntity extends BaseEntity {
  @Column(nullable = false)
  private String name;

  @Column()
  private String description;
}
