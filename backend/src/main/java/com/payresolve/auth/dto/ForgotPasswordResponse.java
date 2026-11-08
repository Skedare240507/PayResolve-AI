package com.payresolve.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForgotPasswordResponse(
    @JsonProperty("message") String message,
    @JsonProperty("resetToken") String resetToken,
    @JsonProperty("resetUrl") String resetUrl,
    @JsonProperty("demoNotice") String demoNotice
) {}

