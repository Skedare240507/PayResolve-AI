package com.payresolve.evidence;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvidenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @BeforeEach
    void setUp() {
        caseService.seedDemoCases();
    }

    @Test
    @DisplayName("GET /api/cases/{id}/graph returns derived evidence graph with missing nodes and dashed edges")
    void shouldReturnDerivedEvidenceGraph() throws Exception {
        mockMvc.perform(get("/api/cases/PR-0142/graph")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caseId").value("PR-0142"))
            .andExpect(jsonPath("$.caseType").value("PAID_NOT_MARKED"))
            .andExpect(jsonPath("$.missingNodesCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.nodes", hasSize(greaterThanOrEqualTo(3))))
            .andExpect(jsonPath("$.nodes[*].isMissing", hasItem(true)))
            .andExpect(jsonPath("$.nodes[*].status", hasItem("MISSING")))
            .andExpect(jsonPath("$.edges", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$.edges[*].isDashed", hasItem(true)));
    }

    @Test
    @DisplayName("GET /api/cases/{id}/timeline returns chronological evidence timeline")
    void shouldReturnChronologicalTimeline() throws Exception {
        mockMvc.perform(get("/api/cases/PR-0142/timeline")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caseId").value("PR-0142"))
            .andExpect(jsonPath("$.totalEvents", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$.items[0].id").isString())
            .andExpect(jsonPath("$.items[0].formattedTime").isString())
            .andExpect(jsonPath("$.items[0].actor").isString())
            .andExpect(jsonPath("$.items[*].isDiscrepancy", hasItem(true)));
    }

    @Test
    @DisplayName("GET /api/cases/{id}/graph returns 404 for unknown case")
    void shouldReturn404ForUnknownCaseGraph() throws Exception {
        mockMvc.perform(get("/api/cases/PR-NONEXISTENT/graph"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/cases/{id}/timeline returns 404 for unknown case")
    void shouldReturn404ForUnknownCaseTimeline() throws Exception {
        mockMvc.perform(get("/api/cases/PR-NONEXISTENT/timeline"))
            .andExpect(status().isNotFound());
    }
}

