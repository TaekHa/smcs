package com.smcs.issue;

/** Thrown when an issue id does not exist. Mapped to 404 {@code ISSUE_NOT_FOUND}. */
public class IssueNotFoundException extends RuntimeException {

	public IssueNotFoundException(Long issueId) {
		super("issue not found: " + issueId);
	}
}
