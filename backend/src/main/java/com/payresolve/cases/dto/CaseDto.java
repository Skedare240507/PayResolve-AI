package com.payresolve.cases.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.cases.CaseRecord;

import java.time.Duration;
import java.time.Instant;

/**
 * Presentation DTO for a case row in the Exception Queue table.
 */
public record CaseDto(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type,
    @JsonProperty("typeLabel") String typeLabel,
    @JsonProperty("order") String order,
    @JsonProperty("amount") String amount,
    @JsonProperty("amountMinor") Long amountMinor,
    @JsonProperty("currency") String currency,
    @JsonProperty("severity") String severity,
    @JsonProperty("age") String age,
    @JsonProperty("status") String status,
    @JsonProperty("openedAt") Instant openedAt,
    @JsonProperty("resolvedAt") Instant resolvedAt,
    @JsonProperty("summary") String summary
) {
    public static CaseDto fromRecord(CaseRecord record, Instant now) {
        String orderLabel = record.shopOrderId() != null
            ? (record.shopOrderId().startsWith("ORD-") ? "#" + record.shopOrderId().substring(4) : record.shopOrderId())
            : (record.paypalOrderId() != null ? record.paypalOrderId() : "#n/a");

        String amountFormatted = record.amount() != null
            ? ("USD".equalsIgnoreCase(record.currency())
                ? String.format(java.util.Locale.ROOT, "$%.2f", record.amount().asMajorUnits())
                : record.amount().toString())
            : "$0.00";

        String ageFormatted = formatAge(record.openedAt(), now);

        return new CaseDto(
            record.id(),
            record.type().name(),
            record.type().displayName(),
            orderLabel,
            amountFormatted,
            record.amountMinor(),
            record.currency() != null ? record.currency() : "USD",
            record.severity().displayName(),
            ageFormatted,
            record.status().displayName(),
            record.openedAt(),
            record.resolvedAt(),
            record.summary()
        );
    }

    private static String formatAge(Instant openedAt, Instant now) {
        if (openedAt == null || now == null) {
            return "just now";
        }
        Duration diff = Duration.between(openedAt, now);
        if (diff.isNegative()) {
            return "just now";
        }
        long minutes = diff.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = diff.toHours();
        if (hours < 24) {
            return hours + " h";
        }
        long days = diff.toDays();
        return days + " d";
    }
}
