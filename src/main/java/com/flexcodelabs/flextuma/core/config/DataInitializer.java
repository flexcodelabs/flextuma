package com.flexcodelabs.flextuma.core.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.flexcodelabs.flextuma.core.services.DataSeederService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DataSeederService seederService;

    @Override
    public void run(String... args) {
        log.info("🚀 FLEXTUMA: Application started - checking system data seeds...");
        try {
            log.info("🌱 FLEXTUMA: Calling seeder service...");
            seederService.seedSystemData();
            log.info("✅ FLEXTUMA: System seeding completed successfully!");
        } catch (Exception e) {
            log.error("❌ FLEXTUMA: System seeding failed: {}", e.getMessage(), e);
            // Don't throw - allow application to continue
        }
    }
}