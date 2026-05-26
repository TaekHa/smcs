package com.smcs.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin-create payload (Story 4.4). Password is NOT supplied — the server generates a
 * one-time temporary password and returns it on the create response only.
 */
public record UserCreateRequest(
		@NotBlank @Size(min = 3, max = 50) String username,
		@NotBlank @Size(max = 50) String displayName,
		@NotBlank @Pattern(regexp = "AGENT|FIELD|ADMIN", message = "role must be AGENT, FIELD, or ADMIN") String role,
		@Size(max = 100) String phone) {
}
