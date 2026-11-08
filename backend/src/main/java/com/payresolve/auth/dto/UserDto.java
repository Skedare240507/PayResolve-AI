package com.payresolve.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.auth.UserRecord;

/**
 * User representation returned to the client upon login / /me check.
 */
public record UserDto(
    @JsonProperty("id") Long id,
    @JsonProperty("username") String username,
    @JsonProperty("email") String email,
    @JsonProperty("role") String role,
    @JsonProperty("authenticated") boolean authenticated
) {
    public static UserDto fromRecord(UserRecord record) {
        return new UserDto(
            record.id(),
            record.username(),
            record.email(),
            record.role(),
            true
        );
    }

    public static UserDto anonymous() {
        return new UserDto(null, "anonymous", "", "ANONYMOUS", false);
    }
}

