package com.smcs.user;

import com.smcs.user.dto.UserAdminResponse;
import com.smcs.user.dto.UserCreateRequest;
import com.smcs.user.dto.UserCreateResponse;
import com.smcs.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only user management (Story 4.4). Create returns the one-time temporary
 * password (201); update is partial and rejects self-deactivation (AC5).
 * The existing public {@code GET /api/users} is left alone — admin work uses a
 * separate {@code /api/admin/users} surface (Story 4.4 pattern, consistent with 4.5).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

	private final UserAdminService userAdminService;

	public AdminUserController(UserAdminService userAdminService) {
		this.userAdminService = userAdminService;
	}

	@GetMapping("/users")
	@PreAuthorize("hasRole('ADMIN')")
	public List<UserAdminResponse> list() {
		return userAdminService.list();
	}

	@PostMapping("/users")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public UserCreateResponse create(@Valid @RequestBody UserCreateRequest request) {
		return userAdminService.create(request);
	}

	@PostMapping("/users/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public UserAdminResponse update(@PathVariable Long id,
			@Valid @RequestBody UserUpdateRequest request,
			@AuthenticationPrincipal Object principal) {
		Long actorId = (Long) principal;
		return userAdminService.update(id, actorId, request);
	}
}
