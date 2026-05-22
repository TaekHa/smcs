package com.smcs.issue;

/** Thrown on an invalid status transition. Mapped to 409 {@code INVALID_TRANSITION}. */
public class IssueTransitionException extends RuntimeException {

	public IssueTransitionException(IssueStatus from, IssueStatus to) {
		super("invalid transition: " + from + " -> " + to);
	}
}
