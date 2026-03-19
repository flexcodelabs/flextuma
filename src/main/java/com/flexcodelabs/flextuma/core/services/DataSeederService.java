package com.flexcodelabs.flextuma.core.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSeederService {

        private final JdbcTemplate jdbcTemplate;
        private final PasswordEncoder passwordEncoder;

        @Transactional
        public void seedSystemData() {
                log.info("🌱 Starting system data seeding...");

                try {
                        // Seed privilege
                        UUID privId = UUID.fromString("5269df21-c8a0-4776-bd89-1015521bc19d");
                        int privResult = jdbcTemplate.update(
                                        "INSERT INTO privilege (id, name, value, system, active, created, updated) " +
                                                        "VALUES (?, 'Super Admin', 'SUPER_ADMIN', true, true, NOW(), NOW()) "
                                                        +
                                                        "ON CONFLICT (id) DO NOTHING",
                                        privId);
                        log.info("🔐 Privilege seeding result: {} rows affected", privResult);

                        // Seed role
                        UUID roleId = UUID.fromString("6269df23-f8a0-4776-bd89-3015521bc19d");
                        int roleResult = jdbcTemplate.update(
                                        "INSERT INTO role (id, name, system, active, created, updated) " +
                                                        "VALUES (?, 'Super Admin', true, true, NOW(), NOW()) " +
                                                        "ON CONFLICT (id) DO NOTHING",
                                        roleId);
                        log.info("👑 Role seeding result: {} rows affected", roleResult);

                        // Link role to privilege
                        int userPrivResult = jdbcTemplate.update(
                                        "INSERT INTO userprivilege (role, privilege) VALUES (?, ?) " +
                                                        "ON CONFLICT DO NOTHING",
                                        roleId, privId);
                        log.info("🔗 Role-Privilege linking result: {} rows affected", userPrivResult);

                        // Seed admin user
                        seedUser(roleId, "admin", "admin@flextuma.com", "Admin123", roleId);

                        // Seed system user
                        seedUser(UUID.fromString("7269df24-g8a0-4776-bd89-4015521bc19d"), "SYSTEM",
                                        "system@flextuma.com", "system_secret_key", roleId);

                        log.info("✅✅✅ System seeding via JDBC completed successfully. ✅✅✅");
                } catch (Exception e) {
                        log.error("❌ System seeding failed: {}", e.getMessage(), e);
                        throw e;
                }
        }

        private void seedUser(UUID userId, String username, String email, String pass, UUID roleId) {
                log.info("👤 Seeding user: {} ({})", username, email);
                String hashedPass = passwordEncoder.encode(pass);

                int userResult = jdbcTemplate.update(
                                "INSERT INTO \"user\" (id, username, name, email, phonenumber, password, type, active, verified, system, password_change_required, created, updated) "
                                                +
                                                "VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM', true, true, true, ?, NOW(), NOW()) "
                                                +
                                                "ON CONFLICT (id) DO NOTHING",
                                userId, username, username.toUpperCase(), email,
                                username.equals("SYSTEM") ? "0000000000" : "123456789",
                                hashedPass, true);
                log.info("👤 User seeding result for {}: {} rows affected", username, userResult);

                int userRoleResult = jdbcTemplate.update(
                                "INSERT INTO userrole (owner, role) VALUES (?, ?) " +
                                                "ON CONFLICT DO NOTHING",
                                userId, roleId);
                log.info("🔗 User-Role linking result for {}: {} rows affected", username, userRoleResult);
        }
}