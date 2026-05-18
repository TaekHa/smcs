package com.smcs.issue;

import com.smcs.issue.dto.CreateIssueRequest;
import com.smcs.issue.dto.IssueListFilter;
import com.smcs.issue.dto.IssueResponse;
import com.smcs.issue.dto.IssueSummary;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IssueController {

	private final IssueService issueService;
	private final IssueQueryService issueQueryService;

	public IssueController(IssueService issueService, IssueQueryService issueQueryService) {
		this.issueService = issueService;
		this.issueQueryService = issueQueryService;
	}

	@GetMapping("/issues")
	@PreAuthorize("hasAnyRole('AGENT','ADMIN')")
	public Page<IssueSummary> list(
			@RequestParam(required = false) List<IssueStatus> status,
			@RequestParam(required = false) List<Long> categoryL1Id,
			@RequestParam(required = false) List<Long> categoryL2Id,
			@RequestParam(required = false) List<Long> categoryL3Id,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q,
			@PageableDefault(size = 50) Pageable pageable) {
		IssueListFilter filter = new IssueListFilter(
				status, categoryL1Id, categoryL2Id, categoryL3Id, assigneeId, from, to, q);
		return issueQueryService.list(filter, pageable);
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
