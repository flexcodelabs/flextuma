package com.flexcodelabs.flextuma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.flexcodelabs.flextuma.core.entities")
@EnableJpaRepositories(basePackages = "com.flexcodelabs.flextuma.core.repositories")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class FlextumaApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlextumaApplication.class, args);
	}
}