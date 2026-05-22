package com.smcs.issue;

import org.springframework.stereotype.Component;

/**
 * Centralizes the §6.3 issue-access rule shared by detail read, activity read, and
 * comment write: AGENT/ADMIN see any issue; FIELD only their assigned issue.
 */
@Component
public class IssueAccessGuard {

	private final IssueRepository issueRepository;

	public IssueAccessGuard(IssueRepository issueRepository) {
		this.issueRepository = issueRepository;
	}

	/**
	 * @param privileged true for AGENT/ADMIN (full access); false for FIELD (assigned-only)
	 * @throws IssueNotFoundException if the issue does not exist
	 * @throws IssueForbiddenException if a non-privileged user is not the assignee
	 */
	public Issue requireAccessible(Long issueId, Long userId, boolean privileged) {
		Issue issue = issueRepository.findById(issueId)
				.orElseThrow(() -> new IssueNotFoundException(issueId));
		if (!privileged && !userId.equals(issue.getAssignedTo())) {
			throw new IssueForbiddenException(issueId);
		}
		return issue;
	}
}
