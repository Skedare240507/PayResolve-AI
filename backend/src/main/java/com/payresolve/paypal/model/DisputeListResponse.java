package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * List response returned by GET /v1/customer/disputes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DisputeListResponse(
    @JsonProperty("items") List<DisputeSummary> items,
    @JsonProperty("links") List<LinkDescription> links,
    @JsonProperty("synthetic") Boolean synthetic,
    @JsonProperty("source") String source
) {
    public DisputeListResponse {
        if (items == null) {
            items = Collections.emptyList();
        }
        if (links == null) {
            links = Collections.emptyList();
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

    public DisputeListResponse withSynthetic(boolean isSynthetic, String src) {
        List<DisputeSummary> mappedItems = items.stream()
            .map(item -> item.withSynthetic(isSynthetic, src))
            .toList();
        return new DisputeListResponse(mappedItems, links, isSynthetic, src);
    }
}
