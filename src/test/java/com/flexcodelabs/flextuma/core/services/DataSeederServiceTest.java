package com.flexcodelabs.flextuma.core.services;

import com.flexcodelabs.flextuma.core.exceptions.MissingSeedConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("6269df23-f8a0-4776-bd89-3015521bc19d");
    private static final UUID SYSTEM_ID = UUID.fromString("7269df24-68a0-4776-bd89-4015521bc19d");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataSeederService service;

    @BeforeEach
    void setUp() {
        service = new DataSeederService(jdbcTemplate, passwordEncoder);
    }

    private void stubAccountExists(UUID userId, boolean exists) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(userId))).thenReturn(exists);
    }

    @Test
    void seedSystemData_shouldThrow_whenAdminMissingAndNoPasswordConfigured() {
        stubAccountExists(ADMIN_ID, false);

        var ex = assertThrows(MissingSeedConfigurationException.class, () -> service.seedSystemData());

        assertTrue(ex.getMessage().contains("FLEXTUMA_ADMIN_SEED_PASSWORD"));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void seedSystemData_shouldThrow_whenSystemMissingAndNoPasswordConfigured() {
        ReflectionTestUtils.setField(service, "adminSeedPassword", "Sup3r$ecret!");
        stubAccountExists(ADMIN_ID, true);
        stubAccountExists(SYSTEM_ID, false);

        var ex = assertThrows(MissingSeedConfigurationException.class, () -> service.seedSystemData());

        assertTrue(ex.getMessage().contains("FLEXTUMA_SYSTEM_SEED_PASSWORD"));
    }

    @Test
    void seedSystemData_shouldSkipBothAccounts_whenAlreadySeeded() {
        stubAccountExists(ADMIN_ID, true);
        stubAccountExists(SYSTEM_ID, true);

        service.seedSystemData();

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void seedSystemData_shouldCreateAdmin_whenPasswordConfiguredAndAccountAbsent() {
        ReflectionTestUtils.setField(service, "adminSeedPassword", "Sup3r$ecret!");
        ReflectionTestUtils.setField(service, "systemSeedPassword", "AnotherSecret!");
        stubAccountExists(ADMIN_ID, false);
        stubAccountExists(SYSTEM_ID, false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        service.seedSystemData();

        verify(passwordEncoder).encode("Sup3r$ecret!");
        verify(passwordEncoder).encode("AnotherSecret!");
    }
}
