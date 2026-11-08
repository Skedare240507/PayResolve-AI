package com.payresolve.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
    @NotBlank @Size(min = 3, max = 50) @JsonProperty("username") String username,
    @NotBlank @Email @JsonProperty("email") String email,
    @NotBlank @Size(min = 6, max = 100) @JsonProperty("password") String password
) {}

