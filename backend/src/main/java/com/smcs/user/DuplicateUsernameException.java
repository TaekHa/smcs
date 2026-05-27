package com.smcs.user;

/** Story 4.4 — thrown by admin create when the requested username already exists. */
public class DuplicateUsernameException extends RuntimeException {

	public DuplicateUsernameException(String username) {
		// NB: username is not PII per §9.1, safe to keep in the exception message for debugging.
		super("Username already in use: " + username);
	}
}
