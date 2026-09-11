package com.flexcodelabs.flextuma.core.config;

import com.flexcodelabs.flextuma.core.exceptions.MissingSeedConfigurationException;
import com.flexcodelabs.flextuma.core.services.DataSeederService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private DataSeederService seederService;

    @Test
    void run_shouldPropagate_whenSeedConfigurationIsMissing() {
        doThrow(new MissingSeedConfigurationException("FLEXTUMA_ADMIN_SEED_PASSWORD must be set"))
                .when(seederService).seedSystemData();

        DataInitializer initializer = new DataInitializer(seederService);

        assertThrows(MissingSeedConfigurationException.class, () -> initializer.run());
    }

    @Test
    void run_shouldSwallow_whenSeedingFailsForAnyOtherReason() {
        doThrow(new RuntimeException("transient DB issue")).when(seederService).seedSystemData();

        DataInitializer initializer = new DataInitializer(seederService);

        assertDoesNotThrow(() -> initializer.run());
    }
}
