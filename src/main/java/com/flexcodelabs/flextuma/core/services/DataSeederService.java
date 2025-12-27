package com.flexcodelabs.flextuma.core.services;

import com.flexcodelabs.flextuma.core.entities.auth.*;
import com.flexcodelabs.flextuma.core.enums.UserType;
import com.flexcodelabs.flextuma.core.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSeederService {

    private final PrivilegeRepository privilegeRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Privilege seedPrivilege(UUID id, String name, String value) {
        return privilegeRepository.findById(id)
                .orElseGet(() -> {
                    Privilege p = new Privilege();
                    p.setId(id);
                    p.setName(name);
                    p.setValue(value);
                    p.setSystem(true);
                    return privilegeRepository.saveAndFlush(p);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Role seedRole(UUID id, String name, Privilege privilege) {
        return roleRepository.findById(id).orElseGet(() -> {
            Role r = new Role();
            r.setId(id);
            r.setName(name);
            r.setSystem(true);
            r.setPrivileges(Set.of(privilege));
            Role saved = roleRepository.saveAndFlush(r);
            log.info("Seeded Role: {}", name);
            return saved;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedUser(UUID id, String username, String email, String pass, Role role) {
        if (!userRepository.existsById(id)) {
            User u = new User();
            u.setId(id);
            u.setName(username);
            u.setUsername(username);
            u.setEmail(email);
            u.setPhoneNumber(username.equalsIgnoreCase("SYSTEM") ? "0000000000" : "123456789");
            u.setPassword(pass);
            u.setType(UserType.SYSTEM);
            u.setActive(true);
            u.setVerified(true);
            u.setRoles(Set.of(role));
            u.setSystem(true);
            userRepository.saveAndFlush(u);
            log.info("Seeded User: {}", username);
        }
    }
}