package com.payresolve.actions;

import com.payresolve.actions.dto.ProposeActionRequest;
import com.payresolve.cases.CaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @Autowired
    private ActionService actionService;

    @Autowired
    private ObjectMapper objectMapper;

    private Long proposedActionId;

    @BeforeEach
    void setUp() {
        caseService.seedDemoCases();

        // Create a known proposed action on PR-0142
        var action = actionService.proposeAction(
            "PR-0142",
            ActionType.SYNC_ORDER_STATUS,
            Map.of("note", "Reconcile unconfirmed shop order with completed PayPal capture"),
            "KEY-TEST-" + System.currentTimeMillis(),
            "AI"
        );
        proposedActionId = action.id();
    }

    @Test
    @DisplayName("Viewer cannot approve: returns HTTP 403 Forbidden")
    void viewerCannotApprove() throws Exception {
        mockMvc.perform(post("/api/actions/" + proposedActionId + "/approve")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Viewer cannot reject: returns HTTP 403 Forbidden")
    void viewerCannotReject() throws Exception {
        mockMvc.perform(post("/api/actions/" + proposedActionId + "/reject")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Operator can approve, double approve is idempotent, and audit log entries are appended")
    void operatorCanApproveAndDoubleApproveIsIdempotent() throws Exception {
        // 1. First approve by operator -> 200 OK
        mockMvc.perform(post("/api/actions/" + proposedActionId + "/approve")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(proposedActionId))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.decidedBy").value("operator"));

        // 2. Second approve by operator -> Idempotent, returns 200 OK with unchanged APPROVED status
        mockMvc.perform(post("/api/actions/" + proposedActionId + "/approve")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(proposedActionId))
            .andExpect(jsonPath("$.status").value("APPROVED"));

        // 3. Verify audit log contains entries
        mockMvc.perform(get("/api/audit")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.content[*].action", hasItems("ACTION_APPROVED", "ORDER_STATUS_SYNCED")));
    }

    @Test
    @DisplayName("Propose action via POST /api/cases/{id}/actions and query case actions")
    void shouldProposeAndQueryActions() throws Exception {
        ProposeActionRequest request = new ProposeActionRequest(
            ActionType.HOLD_REFUND,
            Map.of("reason", "Customer requested dispute investigation"),
            "KEY-HOLD-" + System.currentTimeMillis(),
            "operator"
        );

        MvcResult result = mockMvc.perform(post("/api/cases/PR-0141/actions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caseId").value("PR-0141"))
            .andExpect(jsonPath("$.type").value("HOLD_REFUND"))
            .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
            .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Long actionId = json.get("id").asLong();

        // Query actions for case PR-0141
        mockMvc.perform(get("/api/cases/PR-0141/actions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(actionId.intValue())));
    }
}

