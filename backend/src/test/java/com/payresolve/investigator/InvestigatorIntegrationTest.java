package com.payresolve.investigator;

import com.payresolve.cases.CaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvestigatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        caseService.seedDemoCases();
    }

    @Test
    @DisplayName("POST /api/cases/{id}/investigate runs investigation and returns structured report")
    void shouldRunInvestigationAndReturnReport() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cases/PR-0142/investigate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", startsWith("INV-PR-0142-")))
            .andExpect(jsonPath("$.caseId").value("PR-0142"))
            .andExpect(jsonPath("$.report.status", anyOf(is("CAUSE_FOUND"), is("DEGRADED"))))
            .andExpect(jsonPath("$.report.confirmed", not(empty())))
            .andExpect(jsonPath("$.report.recommendedAction.type").value("SYNC_ORDER_STATUS"))
            .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String investigationId = responseJson.get("id").asText();
        assertThat(investigationId).isNotBlank();

        // Query investigation by ID via GET /api/investigations/{id}
        mockMvc.perform(get("/api/investigations/" + investigationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(investigationId))
            .andExpect(jsonPath("$.caseId").value("PR-0142"))
            .andExpect(jsonPath("$.report.recommendedAction.type").value("SYNC_ORDER_STATUS"));
    }

    @Test
    @DisplayName("POST /api/cases/{id}/investigate returns 404 for unknown case")
    void shouldReturn404ForUnknownCase() throws Exception {
        mockMvc.perform(post("/api/cases/PR-NONEXISTENT/investigate"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/investigations/{id} returns 404 for unknown investigation ID")
    void shouldReturn404ForUnknownInvestigation() throws Exception {
        mockMvc.perform(get("/api/investigations/INV-UNKNOWN-999"))
            .andExpect(status().isNotFound());
    }
}

