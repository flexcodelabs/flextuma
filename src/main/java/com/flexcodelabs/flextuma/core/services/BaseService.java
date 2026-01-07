package com.flexcodelabs.flextuma.core.services;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import com.flexcodelabs.flextuma.core.helpers.GenericSpecification;
import com.flexcodelabs.flextuma.core.security.SecurityUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

public abstract class BaseService<T extends BaseEntity> {

	@PersistenceContext
	protected EntityManager entityManager;

	protected abstract JpaRepository<T, UUID> getRepository();

	protected abstract String getReadPermission();

	protected abstract String getAddPermission();

	protected abstract String getUpdatePermission();

	protected abstract String getDeletePermission();

	public abstract String getEntityPlural();

	public abstract String getPropertyName();

	protected abstract String getEntitySingular();

	protected abstract JpaSpecificationExecutor<T> getRepositoryAsExecutor();

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
	public Pagination<T> findAllPaginated(Pageable pageable, List<String> filter, String fields) {
		checkPermission(getReadPermission());

		Specification<T> spec = (root, query, cb) -> cb.conjunction();

		if (filter != null && !filter.isEmpty()) {
			for (String filterStr : filter) {
				spec = spec.and(new GenericSpecification<>(filterStr));
			}
		}

		Page<T> resultPage = getRepositoryAsExecutor().findAll(spec, pageable);
		return buildPaginatedResponse(resultPage, pageable);
	}

	private Pagination<T> buildPaginatedResponse(Page<T> resultPage, Pageable pageable) {
		return Pagination.<T>builder()
				.page(pageable.getPageNumber() + 1)
				.total(resultPage.getTotalElements())
				.pageSize(pageable.getPageSize())
				.data(resultPage.getContent())
				.build();
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
		T existing = getRepository().findById(id)
				.orElseThrow(() -> new RuntimeException(getEntitySingular() + " not found"));
		onPreUpdate(entity, existing);
		String[] excludedFields = getNullPropertyNames(entity, existing);
		try {
			org.springframework.beans.BeanUtils.copyProperties(entity, existing, excludedFields);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return existing;
	}

	private String[] getNullPropertyNames(T source, T target) {
		final org.springframework.beans.BeanWrapper src = new org.springframework.beans.BeanWrapperImpl(source);
		java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

		java.util.Set<String> emptyNames = new java.util.HashSet<>();

		emptyNames.add("id");
		emptyNames.add("created");
		emptyNames.add("createdBy");
		emptyNames.add("new");
		emptyNames.add("class");

		for (java.beans.PropertyDescriptor pd : pds) {
			Object srcValue = src.getPropertyValue(pd.getName());
			if (srcValue == null) {
				emptyNames.add(pd.getName());
			}
		}

		return emptyNames.toArray(new String[0]);
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

	protected T onPreUpdate(T newEntity, T oldEntity) {
		return newEntity;
	}
}