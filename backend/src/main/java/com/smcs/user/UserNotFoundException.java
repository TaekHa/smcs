package com.smcs.user;

/** Story 4.4 — thrown by the admin update path when the id does not resolve. */
public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(Long id) {
		super("User not found: " + id);
	}
}
