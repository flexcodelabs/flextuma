package com.flexcodelabs.flextuma.core.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregateDTO {
    private String func;
    private String column;
    private String alias;
}
