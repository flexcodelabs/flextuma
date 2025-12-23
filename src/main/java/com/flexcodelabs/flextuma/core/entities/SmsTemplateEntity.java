package com.flexcodelabs.flextuma.core.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "smstemplate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SmsTemplateEntity extends BaseEntity {

	@Column(name = "name", nullable = false, updatable = true)
	private String name;

	@Column(name = "description", nullable = true, updatable = true)
	private String description;

	@Column(name = "content", columnDefinition = "TEXT")
	private String content;

	@Column(name = "category", nullable = true, updatable = true)
	private String category;
}
