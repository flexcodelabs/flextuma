package com.flexcodelabs.flextuma.modules.auth.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, passwordEncoder);

        securityContextHolderMock = Mockito.mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    @Test
    void login_shouldReturnUser_whenCredentialsAreValid() {
        String username = "testuser";
        String password = "password";
        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPassword);

        when(repository.findByUsername(username)).thenReturn(Optional.of(user));

        User result = service.login(username, password);

        assertNotNull(result);
        assertEquals(username, result.getUsername());
    }

    @Test
    void login_shouldThrowException_whenPasswordInvalid() {
        String username = "testuser";
        String password = "password";
        String wrongPassword = "wrong";
        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPassword);

        when(repository.findByUsername(username)).thenReturn(Optional.of(user));

        assertThrows(ResponseStatusException.class, () -> service.login(username, wrongPassword));
    }

    @Test
    void login_shouldThrowException_whenUserNotFound() {
        String username = "unknown";
        when(repository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.login(username, "password"));
    }

    @Test
    void login_shouldLookUpByEmail_whenIdentifierLooksLikeEmail() {
        String email = "jane@example.com";
        String password = "password";
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        User user = new User();
        user.setUsername("jane");
        user.setEmail(email);
        user.setPassword(hashedPassword);

        when(repository.findByEmailWithRoles(email)).thenReturn(Optional.of(user));

        User result = service.login(email, password);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
        verify(repository, never()).findByUsername(any());
    }

    @Test
    void login_shouldNotLookUpByUsername_whenIdentifierLooksLikeEmailButUnknown() {
        String email = "unknown@example.com";
        when(repository.findByEmailWithRoles(email)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.login(email, "password"));
        verify(repository, never()).findByUsername(any());
    }

    @Test
    void checkUsernameAvailability_shouldReportAvailable_withNoSuggestions_whenUsernameIsFree() {
        when(repository.existsByUsername("newname")).thenReturn(false);

        var result = service.checkUsernameAvailability("newname", null, null);

        assertTrue(result.available());
        assertEquals(List.of(), result.suggestions());
        assertNull(result.emailAvailable());
        assertNull(result.phoneAvailable());
    }

    @Test
    void checkUsernameAvailability_shouldSuggestAlternatives_whenUsernameIsTaken() {
        when(repository.existsByUsername("jane")).thenReturn(true);
        when(repository.existsByUsername(argThat(candidate -> candidate.startsWith("jane") && !candidate.equals("jane"))))
                .thenReturn(false);

        var result = service.checkUsernameAvailability("jane", null, null);

        assertFalse(result.available());
        assertEquals(5, result.suggestions().size());
        assertTrue(result.suggestions().stream().allMatch(s -> s.startsWith("jane") && !s.equals("jane")));
        assertEquals(result.suggestions().size(), Set.copyOf(result.suggestions()).size());
    }

    @Test
    void checkUsernameAvailability_shouldSuggestFromEmailLocalPart_whenUsernameTakenAndEmailFree() {
        when(repository.existsByUsername("jane")).thenReturn(true);
        when(repository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(repository.existsByUsername(argThat(candidate -> candidate.startsWith("janedoe") && !candidate.equals("janedoe"))))
                .thenReturn(false);

        var result = service.checkUsernameAvailability("jane", "jane.doe@example.com", null);

        assertFalse(result.available());
        assertTrue(result.emailAvailable());
        assertEquals(5, result.suggestions().size());
        assertTrue(result.suggestions().stream().allMatch(s -> s.startsWith("janedoe")));
    }

    @Test
    void checkUsernameAvailability_shouldSuggestFromUsername_whenUsernameAndEmailBothTaken() {
        when(repository.existsByUsername("jane")).thenReturn(true);
        when(repository.existsByEmail("jane@example.com")).thenReturn(true);
        when(repository.existsByUsername(argThat(candidate -> candidate.startsWith("jane") && !candidate.equals("jane"))))
                .thenReturn(false);

        var result = service.checkUsernameAvailability("jane", "jane@example.com", null);

        assertFalse(result.available());
        assertFalse(result.emailAvailable());
        assertTrue(result.suggestions().stream().allMatch(s -> s.startsWith("jane")));
    }

    @Test
    void checkUsernameAvailability_shouldReportPhoneAvailability_whenPhoneNumberPassed() {
        when(repository.existsByUsername("jane")).thenReturn(false);
        when(repository.existsByPhoneNumber("+255700000000")).thenReturn(true);

        var result = service.checkUsernameAvailability("jane", null, "+255700000000");

        assertTrue(result.available());
        assertFalse(result.phoneAvailable());
    }

    @Test
    void checkUsernameAvailability_shouldThrow_whenUsernameBlank() {
        assertThrows(ResponseStatusException.class, () -> service.checkUsernameAvailability("   ", null, null));
    }

    @Test
    void delete_shouldThrowException_whenUserIsSystem() {
        mockPermissions(Set.of(User.DELETE));
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setSystem(true);

        when(repository.findOne(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<User>>any()))
                .thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () -> service.delete(id));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void changePassword_shouldEncodeAndSaveManagedUser() {
        UUID id = UUID.randomUUID();
        User detachedUser = new User();
        detachedUser.setId(id);

        User managedUser = new User();
        managedUser.setId(id);
        managedUser.setChangePassword(true);

        when(repository.findById(id)).thenReturn(Optional.of(managedUser));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");
        when(repository.save(managedUser)).thenReturn(managedUser);

        User result = service.changePassword(detachedUser, "new-password");

        assertSame(managedUser, result);
        assertEquals("encoded-password", managedUser.getPassword());
        assertFalse(Boolean.TRUE.equals(managedUser.getChangePassword()));
        verify(repository).save(managedUser);
    }

    private void mockPermissions(Set<String> permissions) {
        when(authentication.isAuthenticated()).thenReturn(true);
        List<org.springframework.security.core.GrantedAuthority> authorities = permissions.stream()
                .map(p -> (org.springframework.security.core.GrantedAuthority) () -> p)
                .toList();
        doReturn(authorities).when(authentication).getAuthorities();
    }
}
