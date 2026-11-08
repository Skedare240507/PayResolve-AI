package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of a PayPal customer dispute.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DisputeSummary(
    @JsonProperty("dispute_id") String disputeId,
    @JsonProperty("create_time") String createTime,
    @JsonProperty("update_time") String updateTime,
    @JsonProperty("dispute_amount") Amount disputeAmount,
    @JsonProperty("reason") String reason,
    @JsonProperty("status") String status,
    @JsonProperty("dispute_state") String disputeState,
    @JsonProperty("dispute_life_cycle_stage") String disputeLifeCycleStage,
    @JsonProperty("dispute_channel") String disputeChannel,
    @JsonProperty("synthetic") Boolean synthetic,
    @JsonProperty("source") String source
) {
    public DisputeSummary {
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

    public DisputeSummary withSynthetic(boolean isSynthetic, String src) {
        return new DisputeSummary(
            disputeId,
            createTime,
            updateTime,
            disputeAmount,
            reason,
            status,
            disputeState,
            disputeLifeCycleStage,
            disputeChannel,
            isSynthetic,
            src
        );
    }
}
