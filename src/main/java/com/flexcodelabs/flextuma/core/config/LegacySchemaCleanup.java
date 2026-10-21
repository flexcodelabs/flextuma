package com.flexcodelabs.flextuma.core.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops schema objects that {@code spring.jpa.hibernate.ddl-auto=update} adds but never removes.
 * Each statement is idempotent, so this is safe on every boot.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class LegacySchemaCleanup implements CommandLineRunner {

    // Replaced by unique_provider_url_sender: the old key blocked a second WhatsApp connector
    // (another phone number) on the same Graph API URL for one account.
    private static final String DROP_SMS_CONNECTOR_PROVIDER_URL_KEY =
            "ALTER TABLE smsconnector DROP CONSTRAINT IF EXISTS unique_provider_url";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute(DROP_SMS_CONNECTOR_PROVIDER_URL_KEY);
        } catch (DataAccessException e) {
            log.warn("Legacy schema cleanup skipped: {}", e.getMessage());
        }
    }
}
