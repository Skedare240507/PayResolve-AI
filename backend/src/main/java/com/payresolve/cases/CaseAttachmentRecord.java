package com.payresolve.cases;

import java.time.Instant;

public record CaseAttachmentRecord(
    String id,
    String caseId,
    String filename,
    String contentType,
    long fileSize,
    String dataBase64,
    String extractedData,
    Instant createdAt
) {}

