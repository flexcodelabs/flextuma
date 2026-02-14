package com.flexcodelabs.flextuma.core.entities.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "list", uniqueConstraints = {
                @UniqueConstraint(name = "unique_list", columnNames = { "name", "creator" })
})
@Getter
@Setter
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListEntity extends  AbstractMetadataEntity {

        public static final String PLURAL = "lists";
        public static final String NAME_PLURAL = "Lists";
        public static final String NAME_SINGULAR = "List";
        public static final String READ = "READ_LISTS";
        public static final String ADD = "ADD_LISTS";
        public static final String DELETE = "DELETE_LISTS";
        public static final String UPDATE = "UPDATE_LISTS";
}
