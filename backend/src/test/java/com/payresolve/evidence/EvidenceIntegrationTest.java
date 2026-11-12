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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
                .with(user("viewer").roles("VIEWER"))
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
                .with(user("viewer").roles("VIEWER"))
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
        mockMvc.perform(get("/api/cases/PR-NONEXISTENT/graph")
                .with(user("viewer").roles("VIEWER")))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/cases/{id}/timeline returns 404 for unknown case")
    void shouldReturn404ForUnknownCaseTimeline() throws Exception {
        mockMvc.perform(get("/api/cases/PR-NONEXISTENT/timeline")
                .with(user("viewer").roles("VIEWER")))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Concurrent graph and timeline derivation is idempotent and never throws DuplicateKeyException")
    void concurrentGraphAndTimelineDoNotCollide(
        @Autowired EvidenceService evidenceService,
        @Autowired CaseEvidenceRepository evidenceRepository
    ) throws Exception {
        evidenceRepository.deleteByCaseId("PR-0142");

        java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var graphFuture = pool.submit(() -> {
                startGate.await();
                return evidenceService.deriveGraph("PR-0142");
            });
            var timelineFuture = pool.submit(() -> {
                startGate.await();
                return evidenceService.deriveTimeline("PR-0142");
            });

            startGate.countDown();
            var graph = graphFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var timeline = timelineFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);

            org.assertj.core.api.Assertions.assertThat(graph.caseId()).isEqualTo("PR-0142");
            org.assertj.core.api.Assertions.assertThat(timeline.caseId()).isEqualTo("PR-0142");

            // Calling repository insert twice with same ID is also idempotent
            var existing = evidenceRepository.findByCaseId("PR-0142");
            org.assertj.core.api.Assertions.assertThat(existing).isNotEmpty();
            evidenceRepository.insert(existing.getFirst());
        } finally {
            pool.shutdownNow();
        }
    }
}

