package com.smcs.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	List<User> findByActiveTrueOrderByDisplayNameAsc();

	/** Active users for a role — Story 3.4 uses {@code ADMIN} for report-alert fan-out. */
	List<User> findByRoleAndActiveTrue(User.Role role);

	/** Admin list (Story 4.4) — includes inactive rows, newest first. */
	List<User> findAllByOrderByCreatedAtDesc();

	/** Pre-flight uniqueness check for admin create (Story 4.4) — explicit over CV exception. */
	boolean existsByUsername(String username);
}
