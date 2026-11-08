package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * HATEOAS link representation returned by PayPal APIs.
 */
public record LinkDescription(
    @JsonProperty("href") String href,
    @JsonProperty("rel") String rel,
    @JsonProperty("method") String method
) {
}
