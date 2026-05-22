package com.smcs.issue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum IssueStatus {
	NEW, ASSIGNED, IN_PROGRESS, DONE, VERIFIED;

	/**
	 * Valid transitions via the {@code /transition} endpoint. NEW→ASSIGNED is assignment-only.
	 * Forward moves (Story 2.4); DONE→VERIFIED (검수) and DONE→IN_PROGRESS (재오픈) (Story 2.7,
	 * AGENT/ADMIN only — enforced in IssueService).
	 */
	private static final Map<IssueStatus, Set<IssueStatus>> VALID_TRANSITIONS = Map.of(
			ASSIGNED, EnumSet.of(IN_PROGRESS),
			IN_PROGRESS, EnumSet.of(DONE),
			DONE, EnumSet.of(VERIFIED, IN_PROGRESS)); // Story 2.7: 검수 / 재오픈

	public boolean canTransitionTo(IssueStatus next) {
		return VALID_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(IssueStatus.class)).contains(next);
	}
}
