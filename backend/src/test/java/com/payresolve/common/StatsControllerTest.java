package com.payresolve.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/stats/overview returns real empty or seeded aggregates without errors")
    void getOverviewReturnsAggregatedMetrics() throws Exception {
        // Seed canonical demo cases and benchmark run
        mockMvc.perform(post("/api/cases/seed").with(user("operator").roles("OPERATOR")).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post("/api/benchmark/run").with(user("operator").roles("OPERATOR")).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(get("/api/stats/overview").with(user("viewer").roles("VIEWER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedAt").isNotEmpty())
            .andExpect(jsonPath("$.totalCases", greaterThanOrEqualTo(6)))
            .andExpect(jsonPath("$.casesByType").isArray())
            .andExpect(jsonPath("$.casesBySeverity").isArray())
            .andExpect(jsonPath("$.evidenceCoverageByType").isArray())
            .andExpect(jsonPath("$.webhooksByHour").isArray())
            .andExpect(jsonPath("$.benchmarkComparison.benchmarkId").isNotEmpty())
            .andExpect(jsonPath("$.benchmarkComparison.sampleSize", greaterThan(0)));
    }
}
