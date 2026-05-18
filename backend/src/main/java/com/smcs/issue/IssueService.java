package com.smcs.issue;

import com.smcs.crypto.HmacHasher;
import com.smcs.issue.dto.CreateIssueRequest;
import com.smcs.issue.dto.IssueResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueService {

	private final IssueRepository issueRepository;
	private final IssueEventRepository issueEventRepository;
	private final HmacHasher hmacHasher;

	public IssueService(IssueRepository issueRepository, IssueEventRepository issueEventRepository,
			HmacHasher hmacHasher) {
		this.issueRepository = issueRepository;
		this.issueEventRepository = issueEventRepository;
		this.hmacHasher = hmacHasher;
	}

	/**
	 * Creates a NEW issue and its CREATED event in one transaction.
	 * Caller name/phone are encrypted by the JPA converter on persist;
	 * the phone hash is computed here for exact-match search (Story 2.2).
	 */
	@Transactional
	public IssueResponse create(CreateIssueRequest req, Long currentUserId) {
		String phoneHash = hmacHasher.hashPhone(req.callerPhone());
		Issue issue = new Issue(
				req.title(),
				req.description(),
				req.callerName(),
				req.callerPhone(),
				phoneHash,
				req.categoryL1Id(),
				req.categoryL2Id(),
				req.categoryL3Id(),
				req.priority(),
				currentUserId);
		Issue saved = issueRepository.save(issue);
		issueEventRepository.save(
				new IssueEvent(saved.getId(), currentUserId, IssueEventType.CREATED, null, null));
		return IssueResponse.from(saved);
	}
}
