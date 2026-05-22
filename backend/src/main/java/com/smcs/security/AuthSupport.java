package com.smcs.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Shared authorization helpers for controllers. */
public final class AuthSupport {

	private AuthSupport() {
	}

	/** AGENT/ADMIN have privileged (full) issue access; FIELD is assigned-only (§6.3). */
	public static boolean isPrivileged(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(a -> a.equals("ROLE_AGENT") || a.equals("ROLE_ADMIN"));
	}
}
