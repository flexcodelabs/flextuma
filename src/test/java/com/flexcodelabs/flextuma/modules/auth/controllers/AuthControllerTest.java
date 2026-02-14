package com.flexcodelabs.flextuma.modules.auth.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.dtos.LoginDto;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.services.CookieService;
import com.flexcodelabs.flextuma.modules.auth.services.UserService;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private CookieService cookieService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(userService, cookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void login_shouldReturnUserAndSetCookie_whenCredentialsValid() throws Exception {
        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("user");
        loginDto.setPassword("password");

        User user = new User();
        user.setUsername("user");

        ResponseCookie cookie = ResponseCookie.from("SESSION", "token").build();

        when(userService.login("user", "password")).thenReturn(user);
        when(cookieService.createAuthCookie()).thenReturn(cookie);

        mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    void logout_shouldDeleteCookie() throws Exception {
        ResponseCookie cookie = ResponseCookie.from("SESSION", "").maxAge(0).build();
        when(cookieService.deleteAuthCookie()).thenReturn(cookie);

        mockMvc.perform(post("/api/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void me_shouldReturnUser_whenAuthenticated() throws Exception {
        // For standalone setup, SecurityContextHolder is used by the controller
        // directly.
        // We need to mock SecurityContextHolder.
        // However, standaloneSetup doesn't invoke Spring Security filters, so we can't
        // rely on @WithMockUser.
        // We have to mock the static SecurityContextHolder or inject the context if
        // possible.
        // Since the controller calls SecurityContextHolder.getContext(), we need
        // MockedStatic again or similar approach.
        // But Controller doesn't take SecurityContext as dependency.

        // Let's use Mockito.mockStatic for this test method.
        try (var mocked = mockStatic(SecurityContextHolder.class)) {
            mocked.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn("user");

            User user = new User();
            user.setUsername("user");
            when(userService.findByUsername("user")).thenReturn(user);

            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("user"));
        }
    }

    @Test
    void me_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        try (var mocked = mockStatic(SecurityContextHolder.class)) {
            mocked.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(false);

            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void me_shouldReturnUnauthorized_whenAnonymousUser() throws Exception {
        try (var mocked = mockStatic(SecurityContextHolder.class)) {
            mocked.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            org.springframework.security.authentication.AnonymousAuthenticationToken anonymous = mock(
                    org.springframework.security.authentication.AnonymousAuthenticationToken.class);
            when(securityContext.getAuthentication()).thenReturn(anonymous);
            when(anonymous.isAuthenticated()).thenReturn(true);

            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
