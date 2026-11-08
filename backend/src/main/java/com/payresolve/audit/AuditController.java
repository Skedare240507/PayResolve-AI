package com.payresolve.audit;

import com.payresolve.audit.dto.AuditLogEntryDto;
import com.payresolve.cases.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing paginated access to the append-only audit trail.
 */
@RestController
@RequestMapping("/audit")
@Tag(name = "Audit", description = "Append-only operator actions and system audit trail")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Query audit log records with search, subject filter, and pagination")
    public ResponseEntity<PagedResponse<AuditLogEntryDto>> listAuditLogs(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String subjectType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<AuditLogEntryDto> result = auditService.getAuditLogs(search, subjectType, page, size);
        return ResponseEntity.ok(result);
    }
}

