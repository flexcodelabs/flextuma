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
        UUID privId = UUID.fromString("5269df21-c8a0-4776-bd89-1015521bc19d");
        jdbcTemplate.update(
                "INSERT INTO privilege (id, name, value, system, active, created, updated) " +
                        "VALUES (?, 'Super Admin', 'SUPER_ADMIN', true, true, NOW(), NOW()) " +
                        "ON CONFLICT (id) DO NOTHING",
                privId);

        UUID roleId = UUID.fromString("6269df23-f8a0-4776-bd89-3015521bc19d");
        jdbcTemplate.update(
                "INSERT INTO role (id, name, system, active, created, updated) " +
                        "VALUES (?, 'Super Admin', true, true, NOW(), NOW()) " +
                        "ON CONFLICT (id) DO NOTHING",
                roleId);

        jdbcTemplate.update(
                "INSERT INTO userprivilege (role, privilege) VALUES (?, ?) " +
                        "ON CONFLICT DO NOTHING",
                roleId, privId);

        seedUser(roleId, "admin", "admin@flextuma.com", "Admin123", roleId);

        seedUser(privId, "SYSTEM", "system@flextuma.com", "system_secret_key", roleId);

        log.info("✅✅✅ System seeding via JDBC completed successfully. ✅✅✅");
    }

    private void seedUser(UUID userId, String username, String email, String pass, UUID roleId) {
        String hashedPass = passwordEncoder.encode(pass);

        jdbcTemplate.update(
                "INSERT INTO \"user\" (id, username, name, email, phonenumber, password, type, active, verified, system, created, updated) "
                        +
                        "VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM', true, true, true, NOW(), NOW()) " +
                        "ON CONFLICT (id) DO NOTHING",
                userId, username, username.toUpperCase(), email,
                username.equals("SYSTEM") ? "0000000000" : "123456789",
                hashedPass);

        jdbcTemplate.update(
                "INSERT INTO userrole (owner, role) VALUES (?, ?) " +
                        "ON CONFLICT DO NOTHING",
                userId, roleId);
    }
}