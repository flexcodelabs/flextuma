package com.flexcodelabs.flextuma.core.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityFieldDTO {
    private String name;
    private String type;
    private boolean mandatory;
    private String description;
}
