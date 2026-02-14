package com.flexcodelabs.flextuma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.boot.persistence.autoconfigure.EntityScan(basePackages = "com.flexcodelabs.flextuma.core.entities")
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = "com.flexcodelabs.flextuma.core.repositories")
@org.springframework.data.jpa.repository.config.EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class FlextumaApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlextumaApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.web.client.RestTemplate restTemplate() {
		return new org.springframework.web.client.RestTemplate();
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.web.client.RestClient.Builder restClientBuilder() {
		return org.springframework.web.client.RestClient.builder();
	}
}