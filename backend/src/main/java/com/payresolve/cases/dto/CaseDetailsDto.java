package com.payresolve.cases.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.cases.CaseRecord;

import java.time.Instant;

/**
 * Detailed presentation DTO for an individual case view.
 */
public record CaseDetailsDto(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type,
    @JsonProperty("typeLabel") String typeLabel,
    @JsonProperty("status") String status,
    @JsonProperty("severity") String severity,
    @JsonProperty("paypalOrderId") String paypalOrderId,
    @JsonProperty("shopOrderId") String shopOrderId,
    @JsonProperty("amount") String amount,
    @JsonProperty("amountMinor") Long amountMinor,
    @JsonProperty("currency") String currency,
    @JsonProperty("openedAt") Instant openedAt,
    @JsonProperty("updatedAt") Instant updatedAt,
    @JsonProperty("resolvedAt") Instant resolvedAt,
    @JsonProperty("summary") String summary
) {
    public static CaseDetailsDto fromRecord(CaseRecord record) {
        String amountFormatted = record.amount() != null
            ? ("USD".equalsIgnoreCase(record.currency())
                ? String.format(java.util.Locale.ROOT, "$%.2f", record.amount().asMajorUnits())
                : record.amount().toString())
            : "$0.00";

        return new CaseDetailsDto(
            record.id(),
            record.type().name(),
            record.type().displayName(),
            record.status().displayName(),
            record.severity().displayName(),
            record.paypalOrderId(),
            record.shopOrderId(),
            amountFormatted,
            record.amountMinor(),
            record.currency() != null ? record.currency() : "USD",
            record.openedAt(),
            record.updatedAt(),
            record.resolvedAt(),
            record.summary()
        );
    }
}
