package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Identity user info payload from PayPal (GET /v1/identity/oauth2/userinfo?schema=paypalv1.1).
 * Exposed when the token carries identity scopes; optional under app-level client_credentials.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalUserInfoResponse(
    @JsonProperty("user_id") String userId,
    @JsonProperty("name") String name,
    @JsonProperty("payer_id") String payerId,
    @JsonProperty("emails") List<EmailEntry> emails
) {
    public PayPalUserInfoResponse {
        if (emails == null) {
            emails = Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmailEntry(
        @JsonProperty("value") String value,
        @JsonProperty("primary") Boolean primary
    ) {
    }

    public Optional<String> primaryEmail() {
        return emails.stream()
            .filter(e -> Boolean.TRUE.equals(e.primary()) && e.value() != null && !e.value().isBlank())
            .map(EmailEntry::value)
            .findFirst()
            .or(() -> emails.stream()
                .map(EmailEntry::value)
                .filter(v -> v != null && !v.isBlank())
                .findFirst());
    }
}

