package com.smcs.issue;

/** Thrown when an assignee is not an active FIELD user. Mapped to 400 {@code INVALID_ASSIGNEE}. */
public class InvalidAssigneeException extends RuntimeException {

	public InvalidAssigneeException(Long assigneeId) {
		super("assignee must be an active FIELD user: " + assigneeId);
	}
}
