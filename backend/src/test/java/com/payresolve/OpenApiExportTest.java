package com.payresolve;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Export OpenAPI 3 spec from /api/v3/api-docs to frontend/src/types/openapi.json")
    void exportOpenApiSpec() throws Exception {
        String json = mockMvc.perform(get("/api/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(json).isNotBlank();
        assertThat(json).contains("\"openapi\":");

        java.nio.file.Path targetPath = Paths.get("../frontend/src/types/openapi.json");
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, json);
        System.out.println(">>> Exported OpenAPI 3 specification to " + targetPath.toAbsolutePath() + " (" + json.length() + " bytes)");
    }
}

