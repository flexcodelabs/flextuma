package com.flexcodelabs.flextuma.modules.auth.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;

/**
 * A personal access token grants whoever holds it the full privileges of the
 * account it's attached to, so these guard against a caller using the generic
 * create/update payload to attach a token to someone else's account, revive a
 * revoked one, or plant a hash they already know the plaintext for.
 */
@ExtendWith(MockitoExtension.class)
class PersonalAccessTokenServiceTest {

    @Mock
    private PersonalAccessTokenRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    private PersonalAccessTokenService service;

    @BeforeEach
    void setUp() {
        service = new PersonalAccessTokenService(repository, userRepository);

        securityContextHolderMock = Mockito.mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    private void authenticateAs(String username) {
        when(authentication.getName()).thenReturn(username);
    }

    @Test
    void onPreSave_forcesOwnerToCaller_ignoringClientSuppliedUser() {
        authenticateAs("alice");
        User alice = new User();
        alice.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        User bob = new User();
        bob.setUsername("bob");
        PersonalAccessToken entity = new PersonalAccessToken();
        entity.setUser(bob); // attacker-supplied owner in the request body

        service.onPreSave(entity);

        assertEquals("alice", entity.getUser().getUsername());
    }

    @Test
    void onPreSave_discardsClientSuppliedTokenAndActive_soAFreshSecretIsGenerated() {
        authenticateAs("alice");
        User alice = new User();
        alice.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        String attackerKnownHash = "deadbeef"; // a hash the attacker already knows the plaintext for
        PersonalAccessToken entity = new PersonalAccessToken();
        entity.setToken(attackerKnownHash);
        entity.setActive(true);
        entity.setRawToken("whatever-the-client-sent");

        service.onPreSave(entity);
        assertNull(entity.getToken());
        assertNull(entity.getActive());
        assertNull(entity.getRawToken());

        entity.generateToken(); // the entity's own @PrePersist hook
        assertNotEquals(attackerKnownHash, entity.getToken());
        assertTrue(entity.getRawToken().startsWith("ft_"));
        assertEquals(Boolean.TRUE, entity.getActive());
    }

    @Test
    void onPreUpdate_onlyLetsNameAndExpiryChange() {
        User attacker = new User();
        attacker.setUsername("mallory");

        PersonalAccessToken oldEntity = new PersonalAccessToken();
        oldEntity.setName("CI pipeline");

        PersonalAccessToken incoming = new PersonalAccessToken();
        incoming.setName("Renamed by owner");
        incoming.setExpiresAt(LocalDateTime.now().plusDays(30));
        incoming.setUser(attacker);
        incoming.setToken("attacker-chosen-hash");
        incoming.setActive(true);
        incoming.setScopes(Collections.singleton("MESSAGES_SEND"));
        incoming.setAllowSystemConnectors(true);
        incoming.setRawToken("leaked-back-to-attacker");

        service.onPreUpdate(incoming, oldEntity);

        assertEquals("Renamed by owner", incoming.getName());
        assertNotEquals(null, incoming.getExpiresAt());
        assertNull(incoming.getUser());
        assertNull(incoming.getToken());
        assertNull(incoming.getActive());
        assertNull(incoming.getScopes());
        assertNull(incoming.getAllowedConnectorIds());
        assertNull(incoming.getAllowSystemConnectors());
        assertNull(incoming.getRawToken());
    }
}
