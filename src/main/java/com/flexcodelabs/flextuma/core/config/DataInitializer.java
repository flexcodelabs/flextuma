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
        log.info("FLEXTUMA: Checking system data seeds...");
        try {
            seederService.seedSystemData();
        } catch (Exception e) {
            log.error("FLEXTUMA: Seeding failed: {}", e.getMessage());
        }
    }
}