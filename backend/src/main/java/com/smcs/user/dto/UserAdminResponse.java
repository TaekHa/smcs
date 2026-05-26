package com.smcs.user.dto;

import com.smcs.user.User;
import java.time.Instant;

/**
 * Admin view of a user. {@code passwordHash} is intentionally NEVER exposed —
 * §9.1 secrets/PII must not leak through DTOs even to ADMIN.
 */
public record UserAdminResponse(
		Long id,
		String username,
		String displayName,
		String role,
		String phone,
		boolean active,
		Instant createdAt) {

	public static UserAdminResponse from(User u) {
		return new UserAdminResponse(
				u.getId(),
				u.getUsername(),
				u.getDisplayName(),
				u.getRole().name(),
				u.getPhone(),
				u.isActive(),
				u.getCreatedAt());
	}
}
