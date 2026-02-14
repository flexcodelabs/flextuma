package com.flexcodelabs.flextuma.core.entities.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tag", uniqueConstraints = {
        @UniqueConstraint(name = "unique_tag", columnNames = { "name", "creator" })
})
@Getter
@Setter
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tag extends AbstractMetadataEntity {

    public static final String PLURAL = "tags";
    public static final String NAME_PLURAL = "Tags";
    public static final String NAME_SINGULAR = "Tag";
    public static final String READ = "READ_TAGS";
    public static final String ADD = "ADD_TAGS";
    public static final String DELETE = "DELETE_TAGS";
    public static final String UPDATE = "UPDATE_TAGS";

}
