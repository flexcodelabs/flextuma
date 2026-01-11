package com.flexcodelabs.flextuma.core.entities.contact;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.entities.metadata.ListEntity;
import com.flexcodelabs.flextuma.core.entities.metadata.Tag;
import com.flexcodelabs.flextuma.core.enums.StatusEnum;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contact", uniqueConstraints = {
        @UniqueConstraint(name = "unique_contact", columnNames = { "firstName", "surname", "phoneNumber", "creator" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Contact extends Owner {

    public static final String PLURAL = "contacts";
    public static final String NAME_PLURAL = "Contacts";
    public static final String NAME_SINGULAR = "Contact";
    public static final String READ = "READ_CONTACTS";
    public static final String ADD = "ADD_CONTACTS";
    public static final String DELETE = "DELETE_CONTACTS";
    public static final String UPDATE = "UPDATE_CONTACTS";

    @Column(name = "firstname", nullable = false, updatable = true)
    private String firstName;

    @Column(name = "middlename", nullable = true, updatable = true)
    private String middleName;

    @Column(name = "surname", nullable = false, updatable = true)
    private String surname;

    @Column(name = "phonenumber", nullable = false, updatable = true)
    private String phoneNumber;

    @Column(name = "status", nullable = true, updatable = true)
    @Enumerated(EnumType.STRING)
    private StatusEnum status = StatusEnum.ACTIVE;

    @ManyToMany(cascade = { CascadeType.MERGE })
    @JoinTable(name = "contactlists", joinColumns = @JoinColumn(name = "contact"), inverseJoinColumns = @JoinColumn(name = "list"))
    private Set<ListEntity> lists = new HashSet<>();

    @ManyToMany(cascade = { CascadeType.MERGE })
    @JoinTable(name = "contacttags", joinColumns = @JoinColumn(name = "contact"), inverseJoinColumns = @JoinColumn(name = "tag"))
    private Set<Tag> tags = new HashSet<>();

    public void addToList(ListEntity list) {
        this.lists.add(list);
        list.getContacts().add(this);
    }

    public void addToTag(Tag tag) {
        this.tags.add(tag);
        tag.getContacts().add(this);
    }
}