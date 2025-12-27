package com.flexcodelabs.flextuma.core.config;

import com.flexcodelabs.flextuma.core.entities.auth.Privilege;
import com.flexcodelabs.flextuma.core.entities.auth.Role;
import com.flexcodelabs.flextuma.core.services.DataSeederService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DataSeederService seederService;

    @Override
    public void run(String... args) {
        log.info("Checking system data seeds...");
        try {
            UUID superPrivId = UUID.fromString("5269df21-c8a0-4776-bd89-1015521bc19d");
            Privilege superPriv = seederService.seedPrivilege(superPrivId, "Super Admin", "SUPER_ADMIN");

            UUID superRoleId = UUID.fromString("6269df23-f8a0-4776-bd89-3015521bc19d");
            Role superRole = seederService.seedRole(superRoleId, "Super Admin", superPriv);

            seederService.seedUser(
                    UUID.fromString("6269df23-f8a0-4776-bd89-3015521bc19d"),
                    "admin", "admin@flextuma.com", "Admin123", superRole);

            seederService.seedUser(
                    UUID.fromString("5269df21-c8a0-4776-bd89-1015521bc19d"),
                    "SYSTEM", "system@flextuma.com", "system_secret_key", superRole);

        } catch (Exception e) {
            log.warn("Seeding partial failure (likely item already exists): {}", e.getMessage());
        }
        log.info("Seeding process finished.");
    }
}