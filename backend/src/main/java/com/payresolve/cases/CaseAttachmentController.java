package com.payresolve.cases;

import com.payresolve.ai.LlmResponse;
import com.payresolve.ai.LlmRouter;
import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.evidence.CaseEvidenceRecord;
import com.payresolve.evidence.CaseEvidenceRepository;
import com.payresolve.evidence.EvidenceKind;
import com.payresolve.evidence.EvidenceSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/cases/{caseId}/attachments")
@Tag(name = "Case Attachments", description = "Multimodal evidence upload and AI analysis")
public class CaseAttachmentController {

    private static final Logger log = LoggerFactory.getLogger(CaseAttachmentController.class);

    private final CaseService caseService;
    private final CaseAttachmentRepository attachmentRepository;
    private final CaseEvidenceRepository evidenceRepository;
    private final LlmRouter llmRouter;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public CaseAttachmentController(
        CaseService caseService,
        CaseAttachmentRepository attachmentRepository,
        CaseEvidenceRepository evidenceRepository,
        LlmRouter llmRouter,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        this.caseService = caseService;
        this.attachmentRepository = attachmentRepository;
        this.evidenceRepository = evidenceRepository;
        this.llmRouter = llmRouter;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Operation(summary = "List attachments for a case")
    public List<AttachmentDto> listAttachments(@PathVariable String caseId) {
        // Validate case exists
        caseService.getCaseRecordById(caseId);
        return attachmentRepository.findByCaseId(caseId).stream()
            .map(this::toDto)
            .toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multimodal evidence file (PNG, JPEG, PDF up to 4MB)")
    public ResponseEntity<?> uploadAttachment(
        @PathVariable String caseId,
        @RequestParam("file") MultipartFile file
    ) {
        caseService.getCaseRecordById(caseId);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must not be empty"));
        }

        if (file.getSize() > 4 * 1024 * 1024) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "Attachment exceeds maximum size of 4MB"));
        }

        int count = attachmentRepository.countByCaseId(caseId);
        if (count >= 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum 5 attachments allowed per case"));
        }

        String contentType = detectContentType(file);
        if (contentType == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid file type. Allowed formats: PNG, JPEG, PDF"
            ));
        }

        try {
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String attachmentId = "ATT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            CaseAttachmentRecord record = new CaseAttachmentRecord(
                attachmentId,
                caseId,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "evidence-file",
                contentType,
                file.getSize(),
                base64,
                null,
                clock.instant()
            );

            attachmentRepository.save(record);
            log.info("Saved attachment {} for case {} ({} bytes, type {})", attachmentId, caseId, file.getSize(), contentType);

            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(record));
        } catch (Exception e) {
            log.error("Failed to upload attachment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save attachment"));
        }
    }

    @PostMapping("/{attachmentId}/analyze")
    @Operation(summary = "Trigger AI multimodal extraction for an uploaded attachment")
    public ResponseEntity<?> analyzeAttachment(
        @PathVariable String caseId,
        @PathVariable String attachmentId
    ) {
        caseService.getCaseRecordById(caseId);

        CaseAttachmentRecord attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

        if (!attachment.caseId().equals(caseId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Attachment does not belong to case"));
        }

        String prompt = """
            You are a payment exception investigator analyzing a merchant dispute evidence document.
            Extract the following details from this file in JSON format strictly:
            {
              "merchant": "Merchant name or unknown",
              "amount": 0.00,
              "currency": "USD",
              "date": "YYYY-MM-DD or null",
              "trackingNumber": "Carrier tracking number or null",
              "notes": "Short description of what the document proves"
            }
            """;

        LlmResponse llmResp = llmRouter.routeMultimodalAnalysis(
            caseId,
            attachment.filename(),
            attachment.contentType(),
            attachment.dataBase64(),
            prompt
        );

        String extractedJson = llmResp.content();
        attachmentRepository.updateExtractedData(attachmentId, extractedJson);

        // Normalize as DISPUTE_EVIDENCE in case_evidence so it appears directly in the graph!
        try {
            String evidenceId = "EVD-" + attachmentId;
            CaseEvidenceRecord evdRecord = new CaseEvidenceRecord(
                evidenceId,
                caseId,
                EvidenceKind.DISPUTE_EVIDENCE,
                EvidenceSource.SYSTEM,
                attachmentId,
                extractedJson,
                clock.instant()
            );
            evidenceRepository.insert(evdRecord);
        } catch (Exception e) {
            log.warn("Failed to insert dispute evidence node: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
            "attachmentId", attachmentId,
            "provider", llmResp.provider(),
            "model", llmResp.model(),
            "fallbackUsed", llmResp.fallbackUsed(),
            "fallbackReason", llmResp.fallbackReason() != null ? llmResp.fallbackReason() : "",
            "extractedData", extractedJson
        ));
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = in.read(header);
            if (read >= 4) {
                // PDF magic: %PDF
                if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
                    return "application/pdf";
                }
                // PNG magic: 0x89 P N G
                if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                    return "image/png";
                }
                // JPEG magic: FF D8 FF
                if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                    return "image/jpeg";
                }
            }
        } catch (Exception ignored) {}

        String ct = file.getContentType();
        if ("image/png".equalsIgnoreCase(ct) || "image/jpeg".equalsIgnoreCase(ct) || "application/pdf".equalsIgnoreCase(ct)) {
            return ct.toLowerCase();
        }
        return null;
    }

    private AttachmentDto toDto(CaseAttachmentRecord r) {
        return new AttachmentDto(
            r.id(),
            r.caseId(),
            r.filename(),
            r.contentType(),
            r.fileSize(),
            r.extractedData(),
            r.createdAt()
        );
    }

    public record AttachmentDto(
        String id,
        String caseId,
        String filename,
        String contentType,
        long fileSize,
        String extractedData,
        Instant createdAt
    ) {}
}

