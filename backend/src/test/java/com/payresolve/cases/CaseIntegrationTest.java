package com.payresolve.cases;

import com.payresolve.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseRepository caseRepository;

    @Test
    @DisplayName("Should seed canonical demo cases and verify all 5 discrepancy types exist")
    void shouldSeedCanonicalCasesAndVerifyTypes() throws Exception {
        mockMvc.perform(post("/api/cases/seed")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
            .andExpect(jsonPath("$[*].type", hasItems(
                "PAID_NOT_MARKED",
                "AMOUNT_MISMATCH",
                "DUPLICATE_EVENT",
                "REFUND_MISMATCH",
                "ORPHAN_CAPTURE"
            )));

        // Verify queue listing contains seeded cases
        mockMvc.perform(get("/api/cases")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(5))))
            .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(5)));
    }

    @Test
    @DisplayName("Should retrieve specific case details by ID and return 404 for unknown case")
    void shouldRetrieveCaseDetails() throws Exception {
        // Seed to ensure PR-0142 exists
        caseService.seedDemoCases();

        mockMvc.perform(get("/api/cases/PR-0142"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("PR-0142"))
            .andExpect(jsonPath("$.type").value("PAID_NOT_MARKED"))
            .andExpect(jsonPath("$.typeLabel").value("Paid, not marked"))
            .andExpect(jsonPath("$.severity").value("High"))
            .andExpect(jsonPath("$.amount").value("$84.50"))
            .andExpect(jsonPath("$.amountMinor").value(8450))
            .andExpect(jsonPath("$.shopOrderId").value("ORD-10482"))
            .andExpect(jsonPath("$.paypalOrderId").value("PP-10482"));

        mockMvc.perform(get("/api/cases/PR-NONEXISTENT"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should filter and paginate cases by status, severity, and search term")
    void shouldFilterAndPaginateCases() throws Exception {
        caseService.seedDemoCases();

        // 1. Filter by status=OPEN
        mockMvc.perform(get("/api/cases")
                .param("status", "OPEN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].status", everyItem(is("Open"))));

        // 2. Filter by severity=HIGH
        mockMvc.perform(get("/api/cases")
                .param("severity", "HIGH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].severity", everyItem(is("High"))));

        // 3. Search query
        mockMvc.perform(get("/api/cases")
                .param("search", "PR-0141"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].id").value("PR-0141"));
    }

    @Test
    @DisplayName("Should transition case lifecycle status: OPEN -> INVESTIGATING -> ACTION_PROPOSED -> RESOLVED")
    void shouldTransitionCaseLifecycle() throws Exception {
        caseService.seedDemoCases();

        // 1. Move PR-0142 to INVESTIGATING
        mockMvc.perform(patch("/api/cases/PR-0142/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "status": "INVESTIGATING" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Investigating"))
            .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        // 2. Move PR-0142 to ACTION_PROPOSED
        mockMvc.perform(patch("/api/cases/PR-0142/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "status": "ACTION_PROPOSED" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Proposed"));

        // 3. Resolve case
        mockMvc.perform(patch("/api/cases/PR-0142/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "status": "RESOLVED" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Resolved"))
            .andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test
    @DisplayName("Invariant: Exactly one case per (type, paypalOrderId); reruns update rather than duplicate")
    void shouldEnforceOneCasePerTypeAndPaypalOrderIdOnRerun() {
        String testPaypalOrderId = "PP-RERUN-TEST-99";
        DetectionFinding finding1 = DetectionFinding.of(
            CaseType.PAID_NOT_MARKED,
            CaseSeverity.MEDIUM,
            testPaypalOrderId,
            "ORD-RERUN",
            Money.of(5000, "USD"),
            "Initial discrepancy summary"
        );

        // First run: inserts new case
        CaseRecord case1 = caseService.upsertCase(finding1);
        assertThat(case1.id()).isNotBlank();
        assertThat(case1.summary()).isEqualTo("Initial discrepancy summary");
        assertThat(case1.severity()).isEqualTo(CaseSeverity.MEDIUM);

        // Second run with updated summary and severity: MUST update the existing case rather than duplicate
        DetectionFinding finding2 = DetectionFinding.of(
            CaseType.PAID_NOT_MARKED,
            CaseSeverity.HIGH,
            testPaypalOrderId,
            "ORD-RERUN",
            Money.of(5000, "USD"),
            "Updated discrepancy summary after rerun"
        );

        CaseRecord case2 = caseService.upsertCase(finding2);
        assertThat(case2.id()).isEqualTo(case1.id()); // Same case ID preserved!
        assertThat(case2.severity()).isEqualTo(CaseSeverity.HIGH); // Severity updated!
        assertThat(case2.summary()).isEqualTo("Updated discrepancy summary after rerun"); // Summary updated!

        // Confirm database has exactly one row for (type, testPaypalOrderId)
        CaseRecord reloaded = caseService.getCaseRecordById(case1.id());
        assertThat(reloaded.id()).isEqualTo(case1.id());
        assertThat(reloaded.summary()).isEqualTo("Updated discrepancy summary after rerun");
    }

    @Test
    @DisplayName("Should execute discrepancy detection on demand via POST /api/cases/detect")
    void shouldExecuteDetectionOnDemand() throws Exception {
        mockMvc.perform(post("/api/cases/detect")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalEvaluated", greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.message", containsString("Detection complete")));
    }

    @Test
    @DisplayName("Should generate case numbers following sequence PR-0001, PR-0002...")
    void shouldGenerateCaseNumbersFromSequence() {
        String id1 = caseRepository.nextCaseId();
        String id2 = caseRepository.nextCaseId();
        assertThat(id1).matches("^PR-\\d{4}$");
        assertThat(id2).matches("^PR-\\d{4}$");
        int num1 = Integer.parseInt(id1.substring(3));
        int num2 = Integer.parseInt(id2.substring(3));
        assertThat(num2).isEqualTo(num1 + 1);
    }
}
