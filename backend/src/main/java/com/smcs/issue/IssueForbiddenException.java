package com.smcs.issue;

/** Thrown when a FIELD user accesses an issue not assigned to them. Mapped to 403 {@code ISSUE_FORBIDDEN}. */
public class IssueForbiddenException extends RuntimeException {

	public IssueForbiddenException(Long issueId) {
		super("issue access forbidden: " + issueId);
	}
}
