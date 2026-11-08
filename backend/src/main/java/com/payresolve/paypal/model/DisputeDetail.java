package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Detailed representation of a customer dispute.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DisputeDetail(
    @JsonProperty("dispute_id") String disputeId,
    @JsonProperty("create_time") String createTime,
    @JsonProperty("update_time") String updateTime,
    @JsonProperty("dispute_amount") Amount disputeAmount,
    @JsonProperty("reason") String reason,
    @JsonProperty("status") String status,
    @JsonProperty("dispute_state") String disputeState,
    @JsonProperty("dispute_life_cycle_stage") String disputeLifeCycleStage,
    @JsonProperty("dispute_channel") String disputeChannel,
    @JsonProperty("disputed_transactions") List<DisputedTransaction> disputedTransactions,
    @JsonProperty("messages") List<DisputeMessage> messages,
    @JsonProperty("evidence") List<DisputeEvidence> evidence,
    @JsonProperty("synthetic") Boolean synthetic,
    @JsonProperty("source") String source
) {
    public DisputeDetail {
        if (disputedTransactions == null) {
            disputedTransactions = Collections.emptyList();
        }
        if (messages == null) {
            messages = Collections.emptyList();
        }
        if (evidence == null) {
            evidence = Collections.emptyList();
        }
        if (synthetic == null) {
            synthetic = false;
        }
        if (source == null) {
            source = "PAYPAL";
        }
    }

    public boolean isSynthetic() {
        return Boolean.TRUE.equals(synthetic);
    }

    public DisputeDetail withSynthetic(boolean isSynthetic, String src) {
        return new DisputeDetail(
            disputeId,
            createTime,
            updateTime,
            disputeAmount,
            reason,
            status,
            disputeState,
            disputeLifeCycleStage,
            disputeChannel,
            disputedTransactions,
            messages,
            evidence,
            isSynthetic,
            src
        );
    }
}
