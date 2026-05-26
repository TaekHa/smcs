package com.smcs.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.smcs.attachment.AttachmentLimitException;
import com.smcs.attachment.AttachmentNotFoundException;
import com.smcs.attachment.InvalidImageException;
import com.smcs.category.CategoryNotFoundException;
import com.smcs.issue.InvalidAssigneeException;
import com.smcs.issue.IssueForbiddenException;
import com.smcs.issue.IssueNotFoundException;
import com.smcs.issue.IssueTransitionException;
import com.smcs.issue.ReopenReasonRequiredException;
import com.smcs.issue.export.ExportTooManyRowsException;
import com.smcs.issue.export.UnsupportedFormatException;
import com.smcs.notification.NotificationNotFoundException;
import com.smcs.report.ReportNotFoundException;
import com.smcs.user.DuplicateUsernameException;
import com.smcs.user.SelfDeactivationForbiddenException;
import com.smcs.user.UserNotFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.orElse("validation failed");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("VALIDATION_FAILED", message));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("VALIDATION_FAILED", ex.getName() + ": invalid value"));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("VALIDATION_FAILED", ex.getParameterName() + ": required"));
	}

	@ExceptionHandler(LockedException.class)
	public ResponseEntity<ErrorResponse> handleLocked(LockedException ex) {
		return ResponseEntity.status(HttpStatus.LOCKED)
				.body(ErrorResponse.of("ACCOUNT_LOCKED", ex.getMessage()));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ErrorResponse.of("INVALID_CREDENTIALS", "Invalid username or password."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ErrorResponse.of("FORBIDDEN", "You do not have permission to access this resource."));
	}

	@ExceptionHandler(IssueNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleIssueNotFound(IssueNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("ISSUE_NOT_FOUND", "Issue not found."));
	}

	@ExceptionHandler(IssueForbiddenException.class)
	public ResponseEntity<ErrorResponse> handleIssueForbidden(IssueForbiddenException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ErrorResponse.of("ISSUE_FORBIDDEN", "You do not have access to this issue."));
	}

	@ExceptionHandler(IssueTransitionException.class)
	public ResponseEntity<ErrorResponse> handleInvalidTransition(IssueTransitionException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ErrorResponse.of("INVALID_TRANSITION", "This status transition is not allowed."));
	}

	@ExceptionHandler(ReopenReasonRequiredException.class)
	public ResponseEntity<ErrorResponse> handleReopenReason(ReopenReasonRequiredException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("REOPEN_REASON_REQUIRED", "A reason is required to reopen an issue."));
	}

	@ExceptionHandler(InvalidAssigneeException.class)
	public ResponseEntity<ErrorResponse> handleInvalidAssignee(InvalidAssigneeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("INVALID_ASSIGNEE", "Assignee must be an active field worker."));
	}

	@ExceptionHandler(NotificationNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotificationNotFound(NotificationNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("NOTIFICATION_NOT_FOUND", "Notification not found."));
	}

	@ExceptionHandler(ReportNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleReportNotFound(ReportNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("REPORT_NOT_FOUND", "Report not found."));
	}

	@ExceptionHandler(ExportTooManyRowsException.class)
	public ResponseEntity<ErrorResponse> handleExportTooManyRows(ExportTooManyRowsException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("EXPORT_TOO_MANY_ROWS",
						"결과가 5,000건을 초과합니다(현재 " + ex.getActualCount() + "건). 필터를 좁혀주세요."));
	}

	@ExceptionHandler(UnsupportedFormatException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedFormat(UnsupportedFormatException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("UNSUPPORTED_FORMAT",
						"format: only 'csv' is supported (got '" + ex.getFormat() + "')."));
	}

	@ExceptionHandler(CategoryNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCategoryNotFound(CategoryNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("CATEGORY_NOT_FOUND", "Category not found."));
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("USER_NOT_FOUND", "User not found."));
	}

	@ExceptionHandler(DuplicateUsernameException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateUsername(DuplicateUsernameException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("DUPLICATE_USERNAME", "이미 사용 중인 사용자명입니다."));
	}

	@ExceptionHandler(SelfDeactivationForbiddenException.class)
	public ResponseEntity<ErrorResponse> handleSelfDeactivation(SelfDeactivationForbiddenException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("SELF_DEACTIVATION_FORBIDDEN", "본인 계정은 비활성화할 수 없습니다."));
	}

	@ExceptionHandler(InvalidImageException.class)
	public ResponseEntity<ErrorResponse> handleInvalidImage(InvalidImageException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("INVALID_IMAGE", "Only JPEG/PNG images are allowed."));
	}

	@ExceptionHandler(AttachmentLimitException.class)
	public ResponseEntity<ErrorResponse> handleAttachmentLimit(AttachmentLimitException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("ATTACHMENT_LIMIT", "This issue already has the maximum of 10 attachments."));
	}

	@ExceptionHandler(AttachmentNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleAttachmentNotFound(AttachmentNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("ATTACHMENT_NOT_FOUND", "Attachment not found."));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("IMAGE_TOO_LARGE", "Image exceeds the 10MB limit."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred."));
	}
}
