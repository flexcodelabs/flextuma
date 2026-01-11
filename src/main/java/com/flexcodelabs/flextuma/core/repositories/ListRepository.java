package com.flexcodelabs.flextuma.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.metadata.ListEntity;

import java.util.UUID;

@Repository
public interface ListRepository extends JpaRepository<ListEntity, UUID>,
		JpaSpecificationExecutor<ListEntity> {
}
