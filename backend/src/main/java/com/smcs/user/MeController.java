package com.smcs.user;

import com.smcs.user.dto.UserSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

	private final UserRepository userRepository;

	public MeController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public UserSummary me(@AuthenticationPrincipal Object principal) {
		Long userId = (Long) principal;
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("authenticated user not found in repository"));
		return UserSummary.from(user);
	}
}
