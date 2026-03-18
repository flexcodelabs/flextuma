package com.flexcodelabs.flextuma.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.events.EntityEvent;
import com.flexcodelabs.flextuma.core.helpers.*;
import com.flexcodelabs.flextuma.core.security.SecurityUtils;
import com.flexcodelabs.flextuma.core.dtos.AggregateDTO;
import com.flexcodelabs.flextuma.core.dtos.EntityFieldDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.ParameterizedType;
import java.util.*;

public abstract class BaseService<T extends BaseEntity> {

	@PersistenceContext
	protected EntityManager entityManager;

	protected final Class<T> entityClass;

	@SuppressWarnings("unchecked")
	protected BaseService() {
		this.entityClass = (Class<T>) ((ParameterizedType) getClass()
				.getGenericSuperclass()).getActualTypeArguments()[0];
	}

	private ApplicationEventPublisher eventPublisher;

	@Autowired
	public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	private CurrentUserResolver currentUserResolver;

	@org.springframework.beans.factory.annotation.Autowired
	public void setCurrentUserResolver(CurrentUserResolver currentUserResolver) {
		this.currentUserResolver = currentUserResolver;
	}

	protected abstract JpaRepository<T, UUID> getRepository();

	protected abstract String getReadPermission();

	protected abstract String getAddPermission();

	protected abstract String getUpdatePermission();

	protected abstract String getDeletePermission();

	public abstract String getEntityPlural();

	public abstract String getPropertyName();

	protected abstract String getEntitySingular();

	protected abstract JpaSpecificationExecutor<T> getRepositoryAsExecutor();

	protected abstract String getTableName();

	protected boolean isAdminEntity() {
		return false;
	}

	protected void checkPermission(String requiredPermission) {
		Set<String> authorities = SecurityUtils.getCurrentUserAuthorities();

		boolean isAuthorized = authorities.contains("SUPER_ADMIN") ||
				authorities.contains(requiredPermission) ||
				requiredPermission.equals("ALL");

		if (!isAuthorized) {
			throw new AccessDeniedException("You have no permission to access " + getEntityPlural());
		}
	}

	protected Boolean isAdminPermission() {
		Set<String> authorities = SecurityUtils.getCurrentUserAuthorities();
		return authorities.contains("SUPER_ADMIN");
	}

	protected Specification<T> buildFetchSpec(String fields) {
		return new DynamicFetchSpecification<>(FieldParser.parse(fields));
	}

	@Transactional(readOnly = true)
	public List<EntityFieldDTO> getEntityFields() {
		checkPermission(getReadPermission());
		EntityType<T> type = entityManager.getMetamodel().entity(entityClass);

		return type.getAttributes().stream()
				.map(this::toFieldDTO)
				.sorted(Comparator.comparing(EntityFieldDTO::getName))
				.toList();
	}

	private EntityFieldDTO toFieldDTO(Attribute<?, ?> attribute) {
		boolean mandatory = false;
		if (attribute instanceof jakarta.persistence.metamodel.SingularAttribute) {
			mandatory = !((jakarta.persistence.metamodel.SingularAttribute<?, ?>) attribute).isOptional();
		}

		return EntityFieldDTO.builder()
				.name(attribute.getName())
				.type(attribute.getJavaType().getSimpleName().toUpperCase())
				.mandatory(mandatory)
				.description(attribute.getPersistentAttributeType().name())
				.build();
	}

	protected Specification<T> buildFilterSpec(List<String> filter, String rootJoin) {
		if (filter == null || filter.isEmpty()) {
			return null;
		}
		Specification<T> filterSpec = null;
		for (String filterStr : filter) {
			Specification<T> part = new GenericSpecification<>(filterStr);
			if (filterSpec == null) {
				filterSpec = part;
			} else {
				filterSpec = "OR".equalsIgnoreCase(rootJoin) ? filterSpec.or(part) : filterSpec.and(part);
			}
		}
		return filterSpec;
	}

	@Transactional(readOnly = true)
	public Pagination<T> findAllPaginated(Pageable pageable, List<String> filter, String fields) {
		return doFindAllPaginated(pageable, filter, fields, "AND");
	}

	@Transactional(readOnly = true)
	public Pagination<T> findAllPaginated(Pageable pageable, List<String> filter, String fields, String rootJoin) {
		return doFindAllPaginated(pageable, filter, fields, rootJoin);
	}

	private Pagination<T> doFindAllPaginated(Pageable pageable, List<String> filter, String fields, String rootJoin) {
		checkPermission(getReadPermission());

		Specification<T> spec = buildTenantSpec();
		Specification<T> filterSpec = buildFilterSpec(filter, rootJoin);
		if (filterSpec != null) {
			spec = spec.and(filterSpec);
		}

		if (fields != null && !fields.isBlank()) {
			spec = spec.and(buildFetchSpec(fields));
		}

		Page<T> resultPage = getRepositoryAsExecutor().findAll(spec, pageable);
		return buildPaginatedResponse(resultPage, pageable);
	}

	@SuppressWarnings("unchecked")
	private Specification<T> buildTenantSpec() {
		return currentUserResolver.getCurrentUser()
				.map(user -> (Specification<T>) new TenantAwareSpecification<>(user,
						SecurityUtils.getCurrentUserAuthorities()))
				.orElse((root, query, cb) -> cb.conjunction());
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
		Specification<T> spec = buildTenantSpec();
		return getRepositoryAsExecutor().findAll(spec);
	}

	@Transactional(readOnly = true)
	public List<T> findAll(String fields) {
		return doFindAll(fields, null, "AND");
	}

	@Transactional(readOnly = true)
	public List<T> findAll(String fields, List<String> filter, String rootJoin) {
		return doFindAll(fields, filter, rootJoin);
	}

	private List<T> doFindAll(String fields, List<String> filter, String rootJoin) {
		checkPermission(getReadPermission());
		Specification<T> spec = buildTenantSpec();

		Specification<T> filterSpec = buildFilterSpec(filter, rootJoin);
		if (filterSpec != null) {
			spec = spec.and(filterSpec);
		}

		if (fields != null && !fields.isBlank()) {
			spec = spec.and(buildFetchSpec(fields));
		}
		return getRepositoryAsExecutor().findAll(spec);
	}

	@Transactional(readOnly = true)
	public Optional<T> findById(UUID id) {
		checkPermission(getReadPermission());
		return getRepository().findById(id);
	}

	@Transactional(readOnly = true)
	public Optional<T> findById(UUID id, String fields) {
		checkPermission(getReadPermission());
		Specification<T> spec = buildTenantSpec();
		spec = spec.and((root, query, cb) -> cb.equal(root.get("id"), id));
		if (fields != null && !fields.isBlank()) {
			spec = spec.and(buildFetchSpec(fields));
		}
		return getRepositoryAsExecutor().findOne(spec);
	}

	@Transactional
	public T save(T entity) {
		checkPermission(getAddPermission());
		onPreSave(entity);
		T saved = getRepository().save(entity);
		onPostSave(saved);
		eventPublisher.publishEvent(new EntityEvent<>(this, saved, EntityEvent.EntityEventType.CREATED));
		return saved;
	}

	@Transactional
	public T update(UUID id, T entity) {
		checkPermission(getUpdatePermission());
		T existing = getRepository().findById(id)
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND, getEntitySingular() + " not found"));
		onPreUpdate(entity, existing);
		String[] excludedFields = getNullPropertyNames(entity);
		org.springframework.beans.BeanUtils.copyProperties(entity, existing, excludedFields);
		T saved = getRepository().save(existing);
		eventPublisher.publishEvent(new EntityEvent<>(this, saved, EntityEvent.EntityEventType.UPDATED));
		return saved;
	}

	private String[] getNullPropertyNames(T source) {
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
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND, getEntitySingular() + " not found"));

		validateDelete(entity);

		// Use native query to force deletion
		String tableName = getTableName();
		entityManager.createNativeQuery("DELETE FROM " + tableName + " WHERE id = :id")
				.setParameter("id", id)
				.executeUpdate();

		entityManager.flush();

		onPostDelete(id);
		eventPublisher.publishEvent(new EntityEvent<>(this, entity, EntityEvent.EntityEventType.DELETED));

		return Map.of("message", getEntitySingular() + " deleted successfully");
	}

	@Transactional
	public Map<String, String> deleteMany(List<String> filters) {
		return doDeleteMany(filters, "AND");
	}

	@Transactional
	public Map<String, String> deleteMany(List<String> filters, String rootJoin) {
		return doDeleteMany(filters, rootJoin);
	}

	private Map<String, String> doDeleteMany(List<String> filters, String rootJoin) {
		checkPermission(getDeletePermission());
		Specification<T> spec = buildTenantSpec();
		Specification<T> filterSpec = buildFilterSpec(filters, rootJoin);
		if (filterSpec != null) {
			spec = spec.and(filterSpec);
		}

		List<T> entities = getRepositoryAsExecutor().findAll(spec);
		if (entities.isEmpty()) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND,
					"No " + getEntityPlural() + " found for deletion");
		}

		getRepository().deleteAll(entities);
		return Map.of("message", entities.size() + " " + getEntityPlural() + " deleted successfully");
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> getAggregatedData(List<AggregateDTO> aggregates, List<String> groupBy,
			List<String> filters, String rootJoin) {
		checkPermission(getReadPermission());

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
		Root<T> root = query.from(entityClass);

		List<Selection<?>> selections = new ArrayList<>();

		// Add GroupBy columns to selections
		if (groupBy != null) {
			for (String groupField : groupBy) {
				selections.add(resolvePath(root, groupField).alias(groupField));
			}
		}

		// Add Aggregates to selections
		for (AggregateDTO agg : aggregates) {
			selections.add(createAggregateExpression(cb, root, agg).alias(agg.getAlias()));
		}

		query.select(cb.array(selections.toArray(new Selection[0])));

		// Apply filters
		Specification<T> spec = buildTenantSpec();
		Specification<T> filterSpec = buildFilterSpec(filters, rootJoin);
		if (filterSpec != null) {
			spec = spec.and(filterSpec);
		}

		Predicate predicate = spec.toPredicate(root, query, cb);
		if (predicate != null) {
			query.where(predicate);
		}

		// Apply GroupBy
		if (groupBy != null && !groupBy.isEmpty()) {
			query.groupBy(groupBy.stream()
					.map(f -> (Expression<?>) resolvePath(root, f))
					.toArray(Expression[]::new));
		}

		List<Object[]> results = entityManager.createQuery(query).getResultList();

		return results.stream().map(row -> {
			Map<String, Object> map = new LinkedHashMap<>();
			int i = 0;
			if (groupBy != null) {
				for (String groupField : groupBy) {
					map.put(groupField, row[i++]);
				}
			}
			for (AggregateDTO agg : aggregates) {
				map.put(agg.getAlias(), row[i++]);
			}
			return map;
		}).toList();
	}

	private Expression<?> createAggregateExpression(CriteriaBuilder cb, Root<T> root, AggregateDTO agg) {
		String func = agg.getFunc().toUpperCase();
		Expression<? extends Number> path = resolvePath(root, agg.getColumn()).as(Number.class);

		return switch (func) {
			case "SUM" -> cb.sum(path);
			case "AVG" -> cb.avg(path);
			case "COUNT" -> cb.count(resolvePath(root, agg.getColumn()));
			case "MIN" -> cb.min(path);
			case "MAX" -> cb.max(path);
			default -> throw new IllegalArgumentException("Unsupported aggregation function: " + func);
		};
	}

	private Path<?> resolvePath(Root<T> root, String fieldPath) {
		String[] parts = fieldPath.split("\\.");
		Path<?> path = root;
		for (String part : parts) {
			path = path.get(part);
		}
		return path;
	}

	protected void validateDelete(T entity) {
	}

	protected void onPostDelete(UUID id) {
	}

	protected void onPreSave(T entity) {
	}

	protected void onPostSave(T entity) {
	}

	private final ObjectMapper objectMapper = new ObjectMapper()
			.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	protected T onPreUpdate(T newEntity, T oldEntity) {
		try {
			return objectMapper.updateValue(oldEntity, newEntity);
		} catch (Exception e) {
			return newEntity;
		}
	}
}