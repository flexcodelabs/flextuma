package com.flexcodelabs.flextuma.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseRepository<T, I> extends JpaRepository<T, I> {
    Optional<T> findByCode(String code);

    Optional<T> findByActive(Boolean active);
}