package com.flexcodelabs.flextuma.modules.sms.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsConnectorServiceTest {

    @Mock
    private SmsConnectorRepository repository;

    @Mock
    private CurrentUserResolver userResolver;

    @InjectMocks
    private SmsConnectorService service;

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static SmsConnector saved(User owner, String provider) {
        SmsConnector source = new SmsConnector();
        source.setId(UUID.randomUUID());
        source.setProvider(provider);
        source.setUrl("https://graph.facebook.com/v22.0");
        source.setKey("EAAG-saved-token");
        source.setSenderId("104725069208652");
        source.setCreatedBy(owner);
        return source;
    }

    private static SmsConnector newWhatsAppConnector(UUID reuseKeyFrom) {
        SmsConnector connector = new SmsConnector();
        connector.setProvider("WHATSAPP");
        connector.setSenderId("404725069208999");
        connector.setReuseKeyFrom(reuseKeyFrom);
        return connector;
    }

    @Test
    void onPreSave_shouldCopyKeyAndUrl_fromTheOwnersSavedConnector() {
        User owner = user();
        SmsConnector source = saved(owner, "WHATSAPP");
        when(repository.findById(source.getId())).thenReturn(Optional.of(source));
        when(userResolver.getCurrentUser()).thenReturn(Optional.of(owner));
        SmsConnector connector = newWhatsAppConnector(source.getId());

        service.onPreSave(connector);

        assertEquals("EAAG-saved-token", connector.getKey());
        assertEquals("https://graph.facebook.com/v22.0", connector.getUrl());
        assertEquals("404725069208999", connector.getSenderId());
    }

    @Test
    void onPreSave_shouldRefuseAnotherUsersConnector() {
        SmsConnector source = saved(user(), "WHATSAPP");
        when(repository.findById(source.getId())).thenReturn(Optional.of(source));
        when(userResolver.getCurrentUser()).thenReturn(Optional.of(user()));
        SmsConnector connector = newWhatsAppConnector(source.getId());

        assertThrows(IllegalArgumentException.class, () -> service.onPreSave(connector));
    }

    @Test
    void onPreSave_shouldRefuseAConnectorOfAnotherProvider() {
        User owner = user();
        SmsConnector source = saved(owner, "BEEM");
        when(repository.findById(source.getId())).thenReturn(Optional.of(source));
        SmsConnector connector = newWhatsAppConnector(source.getId());

        assertThrows(IllegalArgumentException.class, () -> service.onPreSave(connector));
    }
}
