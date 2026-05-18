package com.smcs.user;

import com.smcs.user.dto.UserSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal active-user lookup for assignee filter / select (Story 2.2, reused by 2.4).
 * Not admin user management — that is ADMIN-only Story 4.4.
 */
@RestController
@RequestMapping("/api")
public class UserController {

	private final UserRepository userRepository;

	public UserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/users")
	@PreAuthorize("hasAnyRole('AGENT','ADMIN')")
	public List<UserSummary> users() {
		return userRepository.findByActiveTrueOrderByDisplayNameAsc()
				.stream()
				.map(UserSummary::from)
				.toList();
	}
}
