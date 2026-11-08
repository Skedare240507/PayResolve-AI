package com.payresolve.cases;

import com.payresolve.cases.dto.CaseDetailsDto;
import com.payresolve.cases.dto.CaseDto;
import com.payresolve.cases.dto.CaseStatusUpdateRequest;
import com.payresolve.cases.dto.DetectionRunResultDto;
import com.payresolve.cases.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing payment discrepancy cases, queue queries,
 * on-demand detector runs, and lifecycle transitions.
 */
@RestController
@RequestMapping("/cases")
@Tag(name = "Cases", description = "Payment exception cases and detectors")
public class CaseController {

    private static final Logger log = LoggerFactory.getLogger(CaseController.class);

    private final CaseService caseService;
    private final CaseDetectionService caseDetectionService;

    public CaseController(CaseService caseService, CaseDetectionService caseDetectionService) {
        this.caseService = caseService;
        this.caseDetectionService = caseDetectionService;
    }

    @GetMapping
    @Operation(summary = "Query exception cases with optional filtering, search, and pagination")
    public ResponseEntity<PagedResponse<CaseDto>> listCases(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<CaseDto> result = caseService.getCases(status, severity, type, search, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information for a specific exception case")
    public ResponseEntity<CaseDetailsDto> getCaseById(@PathVariable String id) {
        CaseDetailsDto details = caseService.getCaseDetailsById(id);
        return ResponseEntity.ok(details);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Transition case lifecycle status (OPEN, INVESTIGATING, ACTION_PROPOSED, RESOLVED, DISMISSED)")
    public ResponseEntity<CaseDetailsDto> updateStatus(
        @PathVariable String id,
        @Valid @RequestBody CaseStatusUpdateRequest request
    ) {
        CaseStatus newStatus = CaseStatus.fromString(request.status());
        CaseDetailsDto updated = caseService.updateCaseStatus(id, newStatus);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/detect")
    @Operation(summary = "Execute all discrepancy detectors on demand across current system state")
    public ResponseEntity<DetectionRunResultDto> runDetectors() {
        log.info("Received request to run all discrepancy detectors on demand");
        DetectionRunResultDto result = caseDetectionService.runDetection();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/seed")
    @Operation(summary = "Seed canonical demo cases covering all discrepancy types")
    public ResponseEntity<List<CaseDto>> seedCases() {
        log.info("Received request to seed canonical demo cases");
        List<CaseDto> seeded = caseService.seedDemoCases();
        return ResponseEntity.ok(seeded);
    }
}
