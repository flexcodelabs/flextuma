package com.flexcodelabs.flextuma.core.entities.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.enums.CategoryEnum;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "smstemplate", uniqueConstraints = {
		@UniqueConstraint(name = "unique_name_content", columnNames = { "name", "content", "creator" }),
		@UniqueConstraint(name = "unique_code_creator", columnNames = { "code", "creator" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsTemplate extends Owner {

	public static final String PLURAL = "templates";
	public static final String NAME_PLURAL = "SmsTemplates";
	public static final String NAME_SINGULAR = "SmsTemplate";

	public static final String ALL = "ALL";
	public static final String READ = ALL;
	public static final String ADD = ALL;
	public static final String DELETE = ALL;
	public static final String UPDATE = ALL;

	@Column(nullable = true, unique = false)
	private String code;

	@Column(nullable = false, updatable = true)
	private String name;

	@Column(nullable = true, updatable = true)
	private String description;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String content;

	@Column(nullable = true, updatable = true)
	@Enumerated(EnumType.STRING)
	private CategoryEnum category = CategoryEnum.PROMOTIONAL;

	@Column(nullable = true, updatable = true)
	private Boolean system = false;
}
