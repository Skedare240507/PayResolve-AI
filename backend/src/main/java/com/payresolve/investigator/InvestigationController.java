package com.payresolve.investigator;

import com.payresolve.ai.LlmCallRecord;
import com.payresolve.ai.LlmCallRepository;
import com.payresolve.investigator.model.InvestigationResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing payment exception AI investigation endpoints.
 * Endpoints are automatically prefixed with `/api` via WebMvcConfig.
 */
@RestController
@Tag(name = "Investigator", description = "AI Investigator agent endpoints and auditable trace logs")
public class InvestigationController {

    private final InvestigatorService investigatorService;
    private final LlmCallRepository llmCallRepository;

    public InvestigationController(
        InvestigatorService investigatorService,
        LlmCallRepository llmCallRepository
    ) {
        this.investigatorService = investigatorService;
        this.llmCallRepository = llmCallRepository;
    }

    @PostMapping("/cases/{id}/investigate")
    @Operation(summary = "Execute or re-run an AI investigation for a specific exception case")
    public ResponseEntity<InvestigationResponseDto> investigateCase(@PathVariable String id) {
        InvestigationResponseDto response = investigatorService.investigateCase(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{id}/investigations/latest")
    @Operation(
        operationId = "getLatestInvestigationForCase",
        summary = "Retrieve the latest persisted AI investigation and step latencies for a case",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Latest persisted investigation report and tool latencies",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InvestigationResponseDto.class))
            ),
            @ApiResponse(responseCode = "204", description = "No investigation recorded yet for this case")
        }
    )
    public ResponseEntity<InvestigationResponseDto> getLatestInvestigationForCase(@PathVariable String id) {
        return investigatorService.getRecentInvestigationForCase(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/investigations/{id}")
    @Operation(summary = "Retrieve full details and auditable tool trace of an investigation by ID")
    public ResponseEntity<InvestigationResponseDto> getInvestigationById(@PathVariable String id) {
        InvestigationResponseDto response = investigatorService.getInvestigationById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cases/{id}/llm-calls")
    @Operation(summary = "Retrieve all AI LLM calls and provider routing telemetry for a case")
    public ResponseEntity<List<LlmCallRecord>> getCaseLlmCalls(@PathVariable String id) {
        return ResponseEntity.ok(llmCallRepository.findByCaseId(id));
    }

    @GetMapping("/llm-calls/recent")
    @Operation(summary = "Retrieve recent system-wide LLM routing telemetry calls")
    public ResponseEntity<List<LlmCallRecord>> getRecentLlmCalls() {
        return ResponseEntity.ok(llmCallRepository.findRecent(50));
    }
}

