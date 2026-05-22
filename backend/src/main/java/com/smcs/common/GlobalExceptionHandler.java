package com.smcs.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.smcs.issue.InvalidAssigneeException;
import com.smcs.issue.IssueForbiddenException;
import com.smcs.issue.IssueNotFoundException;
import com.smcs.issue.IssueTransitionException;
import com.smcs.notification.NotificationNotFoundException;
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

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred."));
	}
}
