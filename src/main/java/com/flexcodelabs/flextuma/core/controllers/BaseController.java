package com.flexcodelabs.flextuma.core.controllers;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import com.flexcodelabs.flextuma.core.services.BaseService;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class BaseController<T extends BaseEntity, S extends BaseService<T>> {

    protected final S service;

    protected BaseController(S service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> getAll(
            Pageable pageable,
            @RequestParam(required = false, name = "filter") List<String> filters,
            @RequestParam(required = false) String fields) {

        Pagination<T> result = service.findAllPaginated(pageable, filters, fields);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", result.getPage());
        response.put("total", result.getTotal());
        response.put("pageSize", result.getPageSize());
        response.put(service.getPropertyName(), result.getData());

        return response;
    }

    @GetMapping("/{id}")
    public ResponseEntity<T> getById(@PathVariable UUID id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
}
