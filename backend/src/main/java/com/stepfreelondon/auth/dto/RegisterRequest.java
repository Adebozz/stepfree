package com.stepfreelondon.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Ada Lovelace") @NotBlank @Size(max = 100) String fullName,
        @Schema(example = "ada@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(description = "12 to 72 characters (bcrypt's limit is 72 bytes)") @NotBlank @Size(min = 12, max = 72) String password) {

    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=***]";
    }
}
