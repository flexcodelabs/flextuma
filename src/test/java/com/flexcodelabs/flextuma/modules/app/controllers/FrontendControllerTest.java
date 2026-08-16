package com.flexcodelabs.flextuma.modules.app.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FrontendControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<html><body>app</body></html>");
        Files.createDirectories(tempDir.resolve("assets"));
        Files.writeString(tempDir.resolve("assets/app.js"), "console.log('ok');");

        FrontendController controller = new FrontendController();
        ReflectionTestUtils.setField(controller, "frontendDirectory", tempDir.toString());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void serveCatchAll_shouldReturnIndexForBrowserNavigation() throws Exception {
        mockMvc.perform(get("/dashboard/overview").header("Accept", MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().string("<html><body>app</body></html>"));
    }

    @Test
    void serveCatchAll_shouldReturnIndexForLoginRoute() throws Exception {
        mockMvc.perform(get("/login").header("Accept", MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().string("<html><body>app</body></html>"));
    }

    @Test
    void serveCatchAll_shouldReturnNotFoundForNonBrowserRequests() throws Exception {
        mockMvc.perform(get("/wp-login.php").header("Accept", "*/*"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serveAsset_shouldReturnStaticAsset() throws Exception {
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string("console.log('ok');"));
    }
}
