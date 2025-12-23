package com.flexcodelabs.flextuma.core.entities.auth;

import com.flexcodelabs.flextuma.core.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Entity
@Table(name = "role", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

	public static final String PLURAL = "roles";
	public static final String NAME_PLURAL = "Roles";
	public static final String NAME_SINGULAR = "Role";

	public static final String READ = "READ_ROLES";
	public static final String ADD = "ADD_ROLES";
	public static final String DELETE = "DELETE_ROLES";
	public static final String UPDATE = "UPDATE_ROLES";

	@Column(unique = true, nullable = false)
	private String name;

	@Column(name = "system", nullable = false)
	private Boolean system = false;

	@ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinTable(name = "userprivilege", joinColumns = @JoinColumn(name = "role", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "privilege", referencedColumnName = "id"))
	private Set<Privilege> privileges;

	@PrePersist
	public void ensureSystemValue() {
		if (this.system == null) {
			this.system = false;
		}
	}
}
