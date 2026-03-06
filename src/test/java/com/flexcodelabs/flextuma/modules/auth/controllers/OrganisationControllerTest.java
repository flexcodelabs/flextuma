package com.flexcodelabs.flextuma.modules.auth.controllers;

import com.flexcodelabs.flextuma.modules.auth.services.OrganisationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class OrganisationControllerTest {

    @Mock
    private OrganisationService organisationService;

    @InjectMocks
    private OrganisationController organisationController;

    @Test
    void testConstructor() {
        assertNotNull(organisationController);
    }
}
