package com.payresolve.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank @JsonProperty("token") String token,
    @NotBlank @Size(min = 6, max = 100) @JsonProperty("newPassword") String newPassword
) {}

