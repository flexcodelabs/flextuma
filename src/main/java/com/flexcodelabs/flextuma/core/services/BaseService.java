package com.flexcodelabs.flextuma.core.services;

import com.flexcodelabs.flextuma.core.entities.BaseEntity;
import com.flexcodelabs.flextuma.core.security.SecurityUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

public abstract class BaseService<T extends BaseEntity> {

	protected abstract JpaRepository<T, UUID> getRepository();

	protected abstract String getReadPermission();

	protected abstract String getAddPermission();

	protected abstract String getUpdatePermission();

	protected abstract String getDeletePermission();

	protected abstract String getEntityPlural();

	protected abstract String getEntitySingular();

	protected void checkPermission(String requiredPermission) {
		Set<String> authorities = SecurityUtils.getCurrentUserAuthorities();

		boolean isAuthorized = authorities.contains("ALL") ||
				authorities.contains("SUPER_ADMIN") ||
				authorities.contains(requiredPermission);

		if (!isAuthorized) {
			throw new AccessDeniedException("You have no permission to access " + getEntityPlural());
		}
	}

	@Transactional(readOnly = true)
	public List<T> findAll() {
		checkPermission(getReadPermission());
		return getRepository().findAll();
	}

	@Transactional(readOnly = true)
	public Optional<T> findById(UUID id) {
		checkPermission(getReadPermission());
		return getRepository().findById(id);
	}

	@Transactional
	public T save(T entity) {
		checkPermission(getAddPermission());
		onPreSave(entity);
		T saved = getRepository().save(entity);
		onPostSave(saved);
		return saved;
	}

	@Transactional
	public T update(UUID id, T entity) {
		checkPermission(getUpdatePermission());

		return getRepository().findById(id).map(existing -> {
			entity.setId(id);
			onPreUpdate(entity, existing);
			return getRepository().save(entity);
		}).orElseThrow(() -> new RuntimeException(getEntitySingular() + " not found"));
	}

	@Transactional
	public Map<String, String> delete(UUID id) {
		checkPermission(getDeletePermission());

		T entity = getRepository().findById(id)
				.orElseThrow(() -> new RuntimeException(getEntitySingular() + " not found"));

		validateDelete(entity);

		getRepository().deleteById(id);

		onPostDelete(id);

		return Map.of("message", getEntitySingular() + " deleted successfully");
	}

	protected void validateDelete(T entity) {
	}

	protected void onPostDelete(UUID id) {
	}

	protected void onPreSave(T entity) {
	}

	protected void onPostSave(T entity) {
	}

	protected void onPreUpdate(T newEntity, T oldEntity) {
	}
}