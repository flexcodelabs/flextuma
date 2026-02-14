package com.flexcodelabs.flextuma.core.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import com.flexcodelabs.flextuma.core.services.BaseService;

@ExtendWith(MockitoExtension.class)
public abstract class BaseControllerTest<T extends BaseEntity, S extends BaseService<T>> {

    protected MockMvc mockMvc;
    protected ObjectMapper objectMapper = new ObjectMapper();

    protected abstract BaseController<T, S> getController();

    protected abstract S getService();

    protected abstract T createEntity();

    protected abstract String getBaseUrl();

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(getController())
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    public void getAll_shouldReturnPagination() throws Exception {
        T entity = createEntity();
        Pagination<T> pagination = Pagination.<T>builder()
                .page(1)
                .total(1L)
                .pageSize(10)
                .data(List.of(entity))
                .build();

        // Use any() for Pageable since standalone setup might not resolve it perfectly
        // or we don't care about exact instance
        when(getService().findAllPaginated(any(Pageable.class), any(), any())).thenReturn(pagination);
        // Note: BaseController.getAll calls service.getPropertyName().
        // We need to ensure service mock returns something if queried, or Pagination
        // object structure is enough.
        // BaseController uses service.getPropertyName() for the key in the response
        // map.
        when(getService().getPropertyName()).thenReturn("items");

        mockMvc.perform(get(getBaseUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0]").exists());
    }

    @Test
    public void getById_shouldReturnEntity_whenFound() throws Exception {
        UUID id = UUID.randomUUID();
        T entity = createEntity();
        entity.setId(id);

        when(getService().findById(id)).thenReturn(Optional.of(entity));

        mockMvc.perform(get(getBaseUrl() + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    public void getById_shouldReturnNotFound_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(getService().findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get(getBaseUrl() + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    public void create_shouldReturnSavedEntity() throws Exception {
        T entity = createEntity();
        when(getService().save(any())).thenReturn(entity);

        mockMvc.perform(post(getBaseUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entity)))
                .andExpect(status().isOk());
    }

    @Test
    public void update_shouldReturnUpdatedEntity() throws Exception {
        UUID id = UUID.randomUUID();
        T entity = createEntity();
        entity.setId(id);

        when(getService().update(eq(id), any())).thenReturn(entity);

        mockMvc.perform(put(getBaseUrl() + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entity)))
                .andExpect(status().isOk());
    }

    @Test
    public void delete_shouldReturnSuccessMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(getService().delete(id)).thenReturn(Map.of("message", "Deleted"));

        mockMvc.perform(delete(getBaseUrl() + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deleted"));
    }
}
