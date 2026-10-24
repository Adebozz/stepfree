package com.stepfreelondon.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String fullName, String email, Instant createdAt) {
}
