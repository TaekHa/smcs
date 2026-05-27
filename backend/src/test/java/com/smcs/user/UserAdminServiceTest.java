package com.smcs.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smcs.user.dto.UserAdminResponse;
import com.smcs.user.dto.UserCreateRequest;
import com.smcs.user.dto.UserCreateResponse;
import com.smcs.user.dto.UserUpdateRequest;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

	@Mock UserRepository userRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock TemporaryPasswordGenerator temporaryPasswordGenerator;

	private UserAdminService service;

	@BeforeEach
	void setUp() {
		service = new UserAdminService(userRepository, passwordEncoder, temporaryPasswordGenerator);
	}

	private static User user(Long id, String username, User.Role role, boolean active) throws Exception {
		User u = new User(username, "$2a$10$hash", "display-" + username, role, "010-0000-0000");
		setField(u, "id", id);
		setField(u, "active", active);
		setField(u, "createdAt", Instant.parse("2026-05-26T00:00:00Z"));
		setField(u, "updatedAt", Instant.parse("2026-05-26T00:00:00Z"));
		return u;
	}

	@Test
	void createRejectsDuplicateUsernameWithoutHittingSave() {
		when(userRepository.existsByUsername("dup")).thenReturn(true);

		UserCreateRequest req = new UserCreateRequest("dup", "name", "AGENT", null);
		assertThatThrownBy(() -> service.create(req)).isInstanceOf(DuplicateUsernameException.class);

		verify(userRepository, never()).save(any());
		verify(passwordEncoder, never()).encode(anyString());
	}

	@Test
	void createGeneratesTemporaryPasswordHashesItAndReturnsPlaintextOnce() throws Exception {
		when(userRepository.existsByUsername("agent2")).thenReturn(false);
		when(temporaryPasswordGenerator.generate()).thenReturn("Kp9mZ2qR7nXc");
		when(passwordEncoder.encode("Kp9mZ2qR7nXc")).thenReturn("$2a$10$encoded");
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		when(userRepository.save(captor.capture())).thenAnswer(inv -> {
			User saved = inv.getArgument(0);
			setField(saved, "id", 99L);
			setField(saved, "createdAt", Instant.parse("2026-05-26T00:00:00Z"));
			return saved;
		});

		UserCreateResponse res = service.create(new UserCreateRequest("agent2", "에이전트2", "AGENT", "010-1111-2222"));

		assertThat(res.temporaryPassword()).isEqualTo("Kp9mZ2qR7nXc"); // ONLY-once exposure
		assertThat(res.user().username()).isEqualTo("agent2");
		assertThat(res.user().role()).isEqualTo("AGENT");
		// Captured User entity carries the HASH, not the plaintext — defensive check that the
		// service never persists the raw temporary password (§9.1).
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$encoded");
		assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("Kp9mZ2qR7nXc");
	}

	@Test
	void updatePartialPreservesUnsetFields() throws Exception {
		User existing = user(7L, "agent7", User.Role.AGENT, true);
		when(userRepository.findById(7L)).thenReturn(Optional.of(existing));

		UserAdminResponse res = service.update(7L, 999L,
				new UserUpdateRequest(null, "ADMIN", null, null));

		assertThat(res.role()).isEqualTo("ADMIN"); // changed
		assertThat(res.displayName()).isEqualTo("display-agent7"); // preserved
		assertThat(res.phone()).isEqualTo("010-0000-0000"); // preserved
		assertThat(res.active()).isTrue(); // preserved
	}

	@Test
	void updateRejectsSelfDeactivation() throws Exception {
		User self = user(5L, "admin1", User.Role.ADMIN, true);
		when(userRepository.findById(5L)).thenReturn(Optional.of(self));

		// actor (5) === target (5), trying to set active=false → AC5 block
		assertThatThrownBy(() -> service.update(5L, 5L,
				new UserUpdateRequest(null, null, null, false)))
				.isInstanceOf(SelfDeactivationForbiddenException.class);
	}

	@Test
	void updateAllowsSelfActivationToggleOnIfAlreadyTrue() throws Exception {
		// Edge case: actor toggles their OWN active=true. Not a deactivation → must NOT block.
		User self = user(5L, "admin1", User.Role.ADMIN, true);
		when(userRepository.findById(5L)).thenReturn(Optional.of(self));

		UserAdminResponse res = service.update(5L, 5L,
				new UserUpdateRequest(null, null, null, true));
		assertThat(res.active()).isTrue();
	}

	@Test
	void updateAllowsAnotherAdminDeactivatingTarget() throws Exception {
		User target = user(7L, "agent7", User.Role.AGENT, true);
		when(userRepository.findById(7L)).thenReturn(Optional.of(target));

		UserAdminResponse res = service.update(7L, 5L /*different actor*/,
				new UserUpdateRequest(null, null, null, false));
		assertThat(res.active()).isFalse();
	}

	@Test
	void updateThrowsUserNotFoundWhenIdMissing() {
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(999L, 1L,
				new UserUpdateRequest("x", "AGENT", null, true)))
				.isInstanceOf(UserNotFoundException.class);
	}

	private static void setField(Object target, String field, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}
}
