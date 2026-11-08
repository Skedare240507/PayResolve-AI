package com.payresolve.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank @JsonProperty("username") String username,
    @NotBlank @JsonProperty("password") String password
) {}

