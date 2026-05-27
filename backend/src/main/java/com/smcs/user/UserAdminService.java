package com.smcs.user;

import com.smcs.user.dto.UserAdminResponse;
import com.smcs.user.dto.UserCreateRequest;
import com.smcs.user.dto.UserCreateResponse;
import com.smcs.user.dto.UserUpdateRequest;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-side user management (Story 4.4). Soft-delete only — {@code active=false}
 * keeps the row reachable from issues/comments/attachments FK references and lets the
 * existing {@code SmcsUserDetailsService} reject login (AC3). Passwords flow one way:
 * server generates a temporary one on create, hashes it via BCrypt, and never exposes
 * the plaintext again (§9.1).
 */
@Service
public class UserAdminService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryPasswordGenerator temporaryPasswordGenerator;

	public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			TemporaryPasswordGenerator temporaryPasswordGenerator) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.temporaryPasswordGenerator = temporaryPasswordGenerator;
	}

	@Transactional(readOnly = true)
	public List<UserAdminResponse> list() {
		return userRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(UserAdminResponse::from)
				.toList();
	}

	@Transactional
	public UserCreateResponse create(UserCreateRequest req) {
		if (userRepository.existsByUsername(req.username())) {
			throw new DuplicateUsernameException(req.username());
		}
		String temporary = temporaryPasswordGenerator.generate();
		String hash = passwordEncoder.encode(temporary);
		User created = new User(req.username(), hash, req.displayName(),
				User.Role.valueOf(req.role()), req.phone());
		User saved = userRepository.save(created);
		// NB: temporary is returned in-process to the controller; it is never logged here.
		return new UserCreateResponse(UserAdminResponse.from(saved), temporary);
	}

	/**
	 * Partial update — {@code null} fields leave the existing value alone (Deviation #9).
	 * Rejects self-deactivation per AC5 even when the request would otherwise validate.
	 */
	@Transactional
	public UserAdminResponse update(Long id, Long actorId, UserUpdateRequest req) {
		User existing = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException(id));

		Boolean nextActive = req.active();
		if (nextActive != null && !nextActive && id.equals(actorId)) {
			throw new SelfDeactivationForbiddenException();
		}

		String displayName = req.displayName() != null ? req.displayName() : existing.getDisplayName();
		User.Role role = req.role() != null ? User.Role.valueOf(req.role()) : existing.getRole();
		String phone = req.phone() != null ? req.phone() : existing.getPhone();
		boolean active = nextActive != null ? nextActive : existing.isActive();

		existing.updateProfile(displayName, role, phone, active);
		return UserAdminResponse.from(existing);
	}
}
