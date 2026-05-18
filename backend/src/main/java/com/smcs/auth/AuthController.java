package com.smcs.auth;

import com.smcs.auth.dto.LoginRequest;
import com.smcs.auth.dto.LoginResponse;
import com.smcs.security.JwtService;
import com.smcs.security.LoginAttemptService;
import com.smcs.user.SmcsUserDetailsService.SmcsUserPrincipal;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import com.smcs.user.dto.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final LoginAttemptService loginAttemptService;
	private final UserRepository userRepository;

	public AuthController(AuthenticationManager authenticationManager,
			JwtService jwtService,
			LoginAttemptService loginAttemptService,
			UserRepository userRepository) {
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.loginAttemptService = loginAttemptService;
		this.userRepository = userRepository;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		String ip = clientIp(httpRequest);
		if (loginAttemptService.isLocked(request.username(), ip)) {
			throw new LockedException("Account temporarily locked due to too many failed login attempts.");
		}

		Authentication auth;
		try {
			auth = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.username(), request.password()));
		} catch (AuthenticationException ex) {
			loginAttemptService.recordFailure(request.username(), ip);
			throw ex;
		}

		loginAttemptService.recordSuccess(request.username(), ip);
		SmcsUserPrincipal principal = (SmcsUserPrincipal) auth.getPrincipal();
		User.Role role = principal.getRole();
		JwtService.TokenIssued issued = jwtService.generate(principal.getUserId(), role);

		User user = userRepository.findById(principal.getUserId())
				.orElseThrow(() -> new IllegalStateException("authenticated user not found in repository"));
		return new LoginResponse(issued.token(), issued.expiresInSeconds(), UserSummary.from(user));
	}

	private String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
