package com.smcs.issue;

import com.smcs.issue.dto.CreateIssueRequest;
import com.smcs.issue.dto.IssueResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IssueController {

	private final IssueService issueService;

	public IssueController(IssueService issueService) {
		this.issueService = issueService;
	}

	@PostMapping("/issues")
	@PreAuthorize("hasAnyRole('AGENT','ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public IssueResponse create(@Valid @RequestBody CreateIssueRequest request,
			@AuthenticationPrincipal Object principal) {
		Long currentUserId = (Long) principal;
		return issueService.create(request, currentUserId);
	}
}
