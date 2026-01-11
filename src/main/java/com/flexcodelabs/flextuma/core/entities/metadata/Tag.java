package com.flexcodelabs.flextuma.core.entities.metadata;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.entities.contact.Contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tag", uniqueConstraints = {
        @UniqueConstraint(name = "unique_tag", columnNames = { "name", "creator" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tag extends Owner {

    public static final String PLURAL = "tags";
    public static final String NAME_PLURAL = "Tags";
    public static final String NAME_SINGULAR = "Tag";
    public static final String READ = "READ_TAGS";
    public static final String ADD = "ADD_TAGS";
    public static final String DELETE = "DELETE_TAGS";
    public static final String UPDATE = "UPDATE_TAGS";

    @Column(name = "name", nullable = false, updatable = true)
    private String name;

    @Column(name = "description", nullable = true, updatable = true)
    private String description;

    @ManyToMany
    private List<Contact> contacts = List.of();

}
