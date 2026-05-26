package com.smcs.issue;

import com.smcs.comment.CommentService;
import com.smcs.comment.dto.AddCommentRequest;
import com.smcs.comment.dto.CommentResponse;
import com.smcs.issue.dto.AssignRequest;
import com.smcs.issue.dto.CreateIssueRequest;
import com.smcs.issue.dto.IssueActivityResponse;
import com.smcs.issue.dto.IssueDetailResponse;
import com.smcs.issue.dto.TransitionRequest;
import com.smcs.issue.dto.IssueListFilter;
import com.smcs.issue.dto.IssueResponse;
import com.smcs.issue.dto.IssueSummary;
import com.smcs.issue.export.ExportTooManyRowsException;
import com.smcs.issue.export.IssueExportService;
import com.smcs.issue.export.UnsupportedFormatException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IssueController {

	private static final Logger log = LoggerFactory.getLogger(IssueController.class);
	private static final DateTimeFormatter EXPORT_FILENAME_TS =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Seoul"));
	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private final IssueService issueService;
	private final IssueQueryService issueQueryService;
	private final CommentService commentService;
	private final IssueExportService issueExportService;

	public IssueController(IssueService issueService, IssueQueryService issueQueryService,
			CommentService commentService, IssueExportService issueExportService) {
		this.issueService = issueService;
		this.issueQueryService = issueQueryService;
		this.commentService = commentService;
		this.issueExportService = issueExportService;
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

	/**
	 * CSV export of the same {@code GET /issues} query result. ADMIN-only (PRD §6).
	 * Stream-writes UTF-8 with BOM so Excel reads Korean text correctly; {@code includePii}
	 * appends the two caller-PII columns (Story 4.3 Deviation #5).
	 */
	@GetMapping("/issues/export")
	@PreAuthorize("hasRole('ADMIN')")
	public void exportIssues(
			@RequestParam(required = false) List<IssueStatus> status,
			@RequestParam(required = false) List<Long> categoryL1Id,
			@RequestParam(required = false) List<Long> categoryL2Id,
			@RequestParam(required = false) List<Long> categoryL3Id,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "csv") String format,
			@RequestParam(defaultValue = "false") boolean includePii,
			@AuthenticationPrincipal Object principal,
			HttpServletResponse response) throws IOException {

		if (!"csv".equalsIgnoreCase(format)) {
			throw new UnsupportedFormatException(format);
		}

		IssueListFilter filter = new IssueListFilter(
				status, categoryL1Id, categoryL2Id, categoryL3Id, assigneeId, from, to, q);

		// Fail-fast on the 5,000-row cap BEFORE committing the response (status, headers, BOM) —
		// once we've written even one byte, Spring can no longer rewrite a 200/CSV reply into a
		// 400 JSON one and the exception bubbles as a ServletException.
		long count = issueExportService.countMatching(filter);
		if (count > IssueExportService.MAX_ROWS) {
			throw new ExportTooManyRowsException(count);
		}

		String filename = "issues-" + EXPORT_FILENAME_TS.format(ZonedDateTime.now(ZoneId.of("Asia/Seoul"))) + ".csv";
		response.setStatus(HttpStatus.OK.value());
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		// Excel needs an explicit UTF-8 BOM to detect encoding for Korean text (Deviation #7).
		response.getOutputStream().write(UTF8_BOM);

		try (Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
			issueExportService.exportCsv(filter, includePii, writer);
		} catch (UncheckedIOException ex) {
			throw ex.getCause();
		}

		if (includePii) {
			// Deviation #10: audit the privileged path. PII values themselves never logged (§9.1).
			Long actorId = principal instanceof Long ? (Long) principal : null;
			log.info("CSV export with PII: actorId={}, filename={}", actorId, filename);
		}
	}

	// FIELD ownership ("본인 배정만") can't be expressed in @PreAuthorize, so these three
	// only require authentication and delegate ownership to the service (IssueAccessGuard).

	@GetMapping("/me/assigned")
	@PreAuthorize("hasRole('FIELD')")
	public List<IssueSummary> myAssigned(@AuthenticationPrincipal Object principal) {
		Long userId = (Long) principal;
		return issueQueryService.listAssigned(userId);
	}

	@GetMapping("/issues/{id}")
	@PreAuthorize("isAuthenticated()")
	public IssueDetailResponse detail(@PathVariable Long id,
			@AuthenticationPrincipal Object principal, Authentication authentication) {
		Long currentUserId = (Long) principal;
		return issueQueryService.getDetail(id, currentUserId, privileged(authentication));
	}

	@PostMapping("/issues/{id}/comments")
	@PreAuthorize("isAuthenticated()")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody AddCommentRequest request,
			@AuthenticationPrincipal Object principal, Authentication authentication) {
		Long currentUserId = (Long) principal;
		return commentService.addComment(id, currentUserId, privileged(authentication), request);
	}

	@GetMapping("/issues/{id}/events")
	@PreAuthorize("isAuthenticated()")
	public List<IssueActivityResponse> events(@PathVariable Long id,
			@AuthenticationPrincipal Object principal, Authentication authentication) {
		Long currentUserId = (Long) principal;
		return issueQueryService.getActivity(id, currentUserId, privileged(authentication));
	}

	@PostMapping("/issues/{id}/assign")
	@PreAuthorize("hasAnyRole('AGENT','ADMIN')")
	public IssueDetailResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request,
			@AuthenticationPrincipal Object principal, Authentication authentication) {
		Long actorId = (Long) principal;
		issueService.assign(id, request.assigneeId(), actorId);
		return issueQueryService.getDetail(id, actorId, privileged(authentication));
	}

	@PostMapping("/issues/{id}/transition")
	@PreAuthorize("isAuthenticated()")
	public IssueDetailResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request,
			@AuthenticationPrincipal Object principal, Authentication authentication) {
		Long actorId = (Long) principal;
		boolean privileged = privileged(authentication);
		issueService.transition(id, request.to(), actorId, privileged, request.reason());
		return issueQueryService.getDetail(id, actorId, privileged);
	}

	/** AGENT/ADMIN have full issue access; FIELD is assigned-only (§6.3). */
	private boolean privileged(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(a -> a.equals("ROLE_AGENT") || a.equals("ROLE_ADMIN"));
	}
}
