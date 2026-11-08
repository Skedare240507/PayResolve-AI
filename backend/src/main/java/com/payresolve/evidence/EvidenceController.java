package com.payresolve.evidence;

import com.payresolve.evidence.dto.EvidenceGraphDto;
import com.payresolve.evidence.dto.EvidenceTimelineDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing Evidence Graph and Chronological Timeline
 * derived at read-time for exception cases.
 */
@RestController
@RequestMapping("/cases")
@Tag(name = "Evidence", description = "Evidence graph and chronological timeline for cases")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/{id}/graph")
    @Operation(summary = "Get derived evidence graph with concrete and missing nodes for a case")
    public ResponseEntity<EvidenceGraphDto> getCaseGraph(@PathVariable String id) {
        EvidenceGraphDto graph = evidenceService.deriveGraph(id);
        return ResponseEntity.ok(graph);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get chronological evidence timeline for a case")
    public ResponseEntity<EvidenceTimelineDto> getCaseTimeline(@PathVariable String id) {
        EvidenceTimelineDto timeline = evidenceService.deriveTimeline(id);
        return ResponseEntity.ok(timeline);
    }
}

