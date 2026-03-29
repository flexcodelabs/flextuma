package com.flexcodelabs.flextuma.modules.app.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void serveCatchAll_shouldReturnIndexForNonApiRoutes() throws Exception {
        mockMvc.perform(get("/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(content().string("<html><body>app</body></html>"));
    }

    @Test
    void serveCatchAll_shouldReturnIndexForDottedNonApiRoutes() throws Exception {
        mockMvc.perform(get("/foo.bar"))
                .andExpect(status().isOk())
                .andExpect(content().string("<html><body>app</body></html>"));
    }

    @Test
    void serveAsset_shouldReturnStaticAsset() throws Exception {
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string("console.log('ok');"));
    }
}
