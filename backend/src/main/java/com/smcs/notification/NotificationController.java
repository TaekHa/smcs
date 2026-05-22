package com.smcs.notification;

import com.smcs.notification.dto.NotificationResponse;
import com.smcs.notification.dto.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping("/notifications")
	@PreAuthorize("isAuthenticated()")
	public Page<NotificationResponse> list(@AuthenticationPrincipal Object principal,
			@PageableDefault(size = 20) Pageable pageable) {
		return notificationService.list((Long) principal, pageable);
	}

	@GetMapping("/notifications/unread-count")
	@PreAuthorize("isAuthenticated()")
	public UnreadCountResponse unreadCount(@AuthenticationPrincipal Object principal) {
		return new UnreadCountResponse(notificationService.unreadCount((Long) principal));
	}

	@PostMapping("/notifications/{id}/read")
	@PreAuthorize("isAuthenticated()")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void read(@PathVariable Long id, @AuthenticationPrincipal Object principal) {
		notificationService.markRead((Long) principal, id);
	}

	@PostMapping("/notifications/read-all")
	@PreAuthorize("isAuthenticated()")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void readAll(@AuthenticationPrincipal Object principal) {
		notificationService.markAllRead((Long) principal);
	}
}
