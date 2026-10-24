package com.stepfreelondon.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Omitted fields are left unchanged. Changing the email requires the current password. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String fullName,
        @Email @Size(min = 3, max = 254) String email,
        @Schema(description = "Required when changing the email") @Size(max = 72) String currentPassword) {

    @Override
    public String toString() {
        return "UpdateProfileRequest[fullName=" + fullName + ", email=" + email + ", currentPassword=***]";
    }
}
