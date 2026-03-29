package com.flexcodelabs.flextuma.core.controllers;

import com.flexcodelabs.flextuma.core.dtos.AggregateDTO;
import com.flexcodelabs.flextuma.core.dtos.EntityFieldDTO;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import com.flexcodelabs.flextuma.core.services.BaseService;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

public abstract class BaseController<T extends BaseEntity, S extends BaseService<T>> {

    protected final S service;

    protected BaseController(S service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> getAll(
            Pageable pageable,
            @RequestParam(required = false, name = "filter") List<String> filters,
            @RequestParam(required = false) String fields,
            @RequestParam(required = false, defaultValue = "AND") String rootJoin) {

        Pagination<T> result = service.findAllPaginated(pageable, filters, fields, rootJoin);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", result.getPage());
        response.put("total", result.getTotal());
        response.put("pageSize", result.getPageSize());
        response.put(service.getPropertyName(), result.getData());

        return response;
    }

    @GetMapping("/fields")
    public ResponseEntity<List<EntityFieldDTO>> getFields() {
        return ResponseEntity.ok(service.getEntityFields());
    }

    @GetMapping("/{id}")
    public ResponseEntity<T> getById(
            @PathVariable UUID id,
            @RequestParam(required = false) String fields) {
        return service.findById(id, fields)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/aggregate")
    public ResponseEntity<List<Map<String, Object>>> getAggregated(
            @RequestParam(required = false) String aggregate,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false, name = "filter") List<String> filters,
            @RequestParam(required = false, defaultValue = "AND") String rootJoin) {

        List<AggregateDTO> aggregates = parseAggregates(aggregate);
        List<String> groupByFields = groupBy != null ? Arrays.asList(groupBy.split(",")) : null;

        return ResponseEntity.ok(service.getAggregatedData(aggregates, groupByFields, filters, rootJoin));
    }

    private List<AggregateDTO> parseAggregates(String aggregateParam) {
        if (aggregateParam == null || aggregateParam.isBlank()) {
            return List.of();
        }
        return Arrays.stream(aggregateParam.split(","))
                .map(agg -> {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("(\\w+)\\(([\\w\\.\\*]+)\\):(\\w+)")
                            .matcher(agg.trim());
                    if (!matcher.matches()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid aggregation format: " + agg);
                    }
                    return AggregateDTO.builder()
                            .func(matcher.group(1))
                            .column(matcher.group(2))
                            .alias(matcher.group(3))
                            .build();
                })
                .toList();
    }

    @PostMapping
    public ResponseEntity<T> create(@RequestBody T entity) {
        return ResponseEntity.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<T> update(@PathVariable UUID id, @RequestBody T entity) {
        return ResponseEntity.ok(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(service.delete(id));
    }

    @DeleteMapping("/bulky")
    public ResponseEntity<Map<String, String>> deleteBulky(
            @RequestParam(name = "filter") List<String> filters,
            @RequestParam(required = false, defaultValue = "AND") String rootJoin) {
        return ResponseEntity.ok(service.deleteMany(filters, rootJoin));
    }
}
