package com.smcs.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial admin update (Story 4.4). All fields optional — {@code null} means
 * "leave the existing value alone." {@code username} and password are intentionally
 * absent (Story 4.4 #6/#7).
 */
public record UserUpdateRequest(
		@Size(max = 50) String displayName,
		@Pattern(regexp = "AGENT|FIELD|ADMIN", message = "role must be AGENT, FIELD, or ADMIN") String role,
		@Size(max = 100) String phone,
		Boolean active) {
}
