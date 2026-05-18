package com.smcs.user.dto;

import com.smcs.user.User;

public record UserSummary(Long id, String username, String displayName, String role) {

	public static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name());
	}
}
