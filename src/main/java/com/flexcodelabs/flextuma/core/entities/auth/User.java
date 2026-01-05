package com.flexcodelabs.flextuma.core.entities.auth;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.security.crypto.bcrypt.BCrypt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flexcodelabs.flextuma.core.entities.base.NameEntity;
import com.flexcodelabs.flextuma.core.enums.UserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "\"user\"", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends NameEntity {

	public static final String PLURAL = "users";

	public static final String NAME_PLURAL = "Users";
	public static final String NAME_SINGULAR = "User";

	public static final String READ = "READ_USERS";
	public static final String ADD = "ADD_USERS";
	public static final String DELETE = "DELETE_USERS";
	public static final String UPDATE = "UPDATE_USERS";

	@Column(name = "phonenumber", unique = true)
	@NotEmpty(message = "Phone number is required")
	private String phoneNumber;

	@Column(name = "lastlogin")
	private LocalDateTime lastLogin;

	@Column(unique = true)
	private String email;

	@Column(unique = true)
	private String username;

	@NotEmpty(message = "Password is required")
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String salt;

	private LocalDateTime expires;

	@Enumerated(EnumType.STRING)
	private UserType type = UserType.SYSTEM;

	private boolean verified = false;

	@Column(name = "system", nullable = false)
	private Boolean system = false;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "userrole", joinColumns = @JoinColumn(name = "owner", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "role", referencedColumnName = "id"))
	private Set<Role> roles;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "creator", referencedColumnName = "id")
	private User createdBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updator", referencedColumnName = "id")
	private User updatedBy;

	@PrePersist
	public void onPrePersist() {
		if (this.createdBy != null) {
			this.setActive(true);
		}

		if (this.password != null && (this.salt == null)) {
			this.salt = BCrypt.gensalt();
			this.password = BCrypt.hashpw(this.password, this.salt);
		}

		this.phoneNumber = validateUserPhone(this.phoneNumber);
	}

	private String validateUserPhone(String phone) {
		if (phone == null)
			return null;
		return phone.trim();
	}

	public boolean validatePassword(String plainPassword) {
		return BCrypt.checkpw(plainPassword, this.password);
	}
}