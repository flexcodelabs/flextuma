package com.flexcodelabs.flextuma.modules.auth.services;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.dtos.RegisterDto;
import com.flexcodelabs.flextuma.core.dtos.ProfileUpdateDto;
import com.flexcodelabs.flextuma.core.dtos.UsernameAvailabilityDto;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service

@RequiredArgsConstructor
public class UserService extends BaseService<User> {
    private static final int MAX_USERNAME_SUGGESTIONS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected boolean isAdminEntity() {
        return true;
    }

    @Override
    protected JpaRepository<User, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return User.READ;
    }

    @Override
    protected String getAddPermission() {
        return User.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return User.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return User.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return User.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return User.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return User.NAME_SINGULAR;
    }

    @Override
    protected String getTableName() {
        return "\"user\"";
    }

    @Override
    protected JpaSpecificationExecutor<User> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected void validateDelete(User user) {
        if (Boolean.TRUE.equals(user.getSystem())) {
            throw new IllegalStateException("System users cannot be deleted");
        }
    }

    public User login(String identifier, String password) {
        boolean looksLikeEmail = identifier != null && identifier.contains("@");
        Optional<User> found = looksLikeEmail
                ? repository.findByEmailWithRoles(identifier)
                : repository.findByUsername(identifier);
        User user = found.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Invalid username or password"));
        if (!user.validatePassword(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return user;
    }

    public AuthenticationResult authenticateAndCreateContext(String username, String password,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = login(username, password);

        java.util.Set<SimpleGrantedAuthority> authorities = user.getRoles()
                .stream()
                .flatMap(role -> role.getPrivileges().stream())
                .map(privilege -> new SimpleGrantedAuthority(privilege.getValue()))
                .collect(java.util.stream.Collectors.toSet());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, authorities);
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(httpRequest));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return new AuthenticationResult(user, authentication);
    }

    public User findByUsername(String username) {
        return repository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User with username " + username + " not found"));
    }

    /** Public, unauthenticated username-availability check backing the signup form's live
     * validation. When taken, suggests alternatives so the caller isn't left to guess one --
     * built from the email's local part (before '@') when an email was passed and it's still
     * free, since that's more likely to read as "theirs" than a random suffix. */
    public UsernameAvailabilityDto checkUsernameAvailability(String rawUsername, String rawEmail,
            String rawPhoneNumber) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (username.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is too long");
        }

        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        Boolean emailAvailable = null;
        String emailLocalPart = null;
        if (!email.isBlank()) {
            emailAvailable = !repository.existsByEmail(email);
            int at = email.indexOf('@');
            if (at > 0) {
                emailLocalPart = email.substring(0, at);
            }
        }

        String phoneNumber = rawPhoneNumber == null ? "" : rawPhoneNumber.trim();
        Boolean phoneAvailable = phoneNumber.isBlank() ? null : !repository.existsByPhoneNumber(phoneNumber);

        boolean available = !repository.existsByUsername(username);
        List<String> suggestions;
        if (available) {
            suggestions = List.of();
        } else {
            String suggestionBase = Boolean.TRUE.equals(emailAvailable) && emailLocalPart != null
                    ? emailLocalPart
                    : username;
            suggestions = generateAvailableUsernames(suggestionBase);
        }
        return new UsernameAvailabilityDto(username, available, suggestions, emailAvailable, phoneAvailable);
    }

    private List<String> generateAvailableUsernames(String requested) {
        String base = requested.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (base.isBlank()) {
            base = "user";
        }

        List<String> suggestions = new ArrayList<>();
        // Bounded so a base that happens to collide with every random suffix (astronomically
        // unlikely, but not impossible) can't spin this into an unbounded loop.
        int maxAttempts = MAX_USERNAME_SUGGESTIONS * 20;
        for (int attempt = 0; suggestions.size() < MAX_USERNAME_SUGGESTIONS && attempt < maxAttempts; attempt++) {
            String candidate = base + (1000 + RANDOM.nextInt(9000));
            if (!suggestions.contains(candidate) && !repository.existsByUsername(candidate)) {
                suggestions.add(candidate);
            }
        }
        return suggestions;
    }

    public User register(RegisterDto request) {
        repository.findByUsername(request.getUsername()).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User with username " + request.getUsername() + " already exists");
        });
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            repository.findByEmail(request.getEmail()).ifPresent(u -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User with email " + request.getEmail() + " already exists");
            });
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            repository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(u -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User with phone number " + request.getPhoneNumber() + " already exists");
            });
        }

        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());

        return repository.save(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public User updateProfile(String currentUsername, ProfileUpdateDto request) {
        User user = findByUsername(currentUsername);
        repository.findByUsername(request.username())
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists"); });
        repository.findByEmail(request.email())
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists"); });
        repository.findByPhoneNumber(request.phoneNumber())
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number already exists"); });
        user.setName(request.name());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        return repository.save(user);
    }

    public User changePassword(User user, String newPassword) {
        User managedUser = repository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        managedUser.setPassword(passwordEncoder.encode(newPassword));
        managedUser.setChangePassword(false);

        return repository.save(managedUser);
    }

}
