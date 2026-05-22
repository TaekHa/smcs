package com.smcs.issue;

/** Thrown when a reopen (DONE→IN_PROGRESS) is requested without a reason. 400 {@code REOPEN_REASON_REQUIRED}. */
public class ReopenReasonRequiredException extends RuntimeException {

	public ReopenReasonRequiredException(Long issueId) {
		super("reopen reason is required for issue: " + issueId);
	}
}
