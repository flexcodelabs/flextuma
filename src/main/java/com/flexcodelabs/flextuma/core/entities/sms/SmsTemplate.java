package com.flexcodelabs.flextuma.core.entities.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.Owner;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "smstemplate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsTemplate extends Owner {

	public static final String PLURAL = "smsTemplates";
	public static final String NAME_PLURAL = "SMS Templates";
	public static final String NAME_SINGULAR = "SMS Template";

	public static final String READ = "READ_SMS_TEMPLATES";
	public static final String ADD = "ADD_SMS_TEMPLATES";
	public static final String DELETE = "DELETE_SMS_TEMPLATES";
	public static final String UPDATE = "UPDATE_SMS_TEMPLATES";

	@Column(name = "name", nullable = false, updatable = true)
	private String name;

	@Column(name = "description", nullable = true, updatable = true)
	private String description;

	@Column(name = "content", columnDefinition = "TEXT", nullable = false)
	private String content;

	@Column(name = "category", nullable = true, updatable = true)
	private String category;
}
