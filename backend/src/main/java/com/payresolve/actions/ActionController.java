package com.payresolve.actions;

import com.payresolve.actions.dto.ProposeActionRequest;
import com.payresolve.actions.dto.ProposedActionDto;
import com.payresolve.cases.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for human-in-the-loop action proposals, approvals, rejections,
 * and querying action histories.
 * Approvals and rejections are strictly restricted to users with the OPERATOR role.
 */
@RestController
@Tag(name = "Actions", description = "Human-in-the-loop action proposals and decision executions")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping("/cases/{id}/actions")
    @Operation(summary = "Propose an action for an exception case (idempotent)")
    public ResponseEntity<ProposedActionDto> proposeAction(
        @PathVariable String id,
        @Valid @RequestBody ProposeActionRequest request,
        Authentication authentication
    ) {
        String actor = (authentication != null && authentication.getName() != null)
            ? authentication.getName()
            : request.proposedBy();

        ProposedActionDto dto = actionService.proposeAction(
            id,
            request.type(),
            request.payload(),
            request.idempotencyKey(),
            actor
        );
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/cases/{id}/actions")
    @Operation(summary = "List all proposed actions associated with a specific case")
    public ResponseEntity<List<ProposedActionDto>> getActionsForCase(@PathVariable String id) {
        List<ProposedActionDto> actions = actionService.getActionsByCaseId(id);
        return ResponseEntity.ok(actions);
    }

    @GetMapping("/actions")
    @Operation(summary = "Query all proposed actions across cases with status filtering and pagination")
    public ResponseEntity<PagedResponse<ProposedActionDto>> listActions(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<ProposedActionDto> result = actionService.listActions(status, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/actions/{id}")
    @Operation(summary = "Get detailed information for a specific proposed action")
    public ResponseEntity<ProposedActionDto> getActionById(@PathVariable Long id) {
        ProposedActionDto dto = actionService.getActionById(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/actions/{id}/approve")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Approve and execute a proposed action (OPERATOR only, idempotent)")
    public ResponseEntity<ProposedActionDto> approveAction(
        @PathVariable Long id,
        Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "operator";
        ProposedActionDto dto = actionService.approveAction(id, actor);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/actions/{id}/reject")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Reject a proposed action (OPERATOR only, idempotent)")
    public ResponseEntity<ProposedActionDto> rejectAction(
        @PathVariable Long id,
        Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "operator";
        ProposedActionDto dto = actionService.rejectAction(id, actor);
        return ResponseEntity.ok(dto);
    }
}

