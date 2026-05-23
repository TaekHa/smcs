package com.smcs.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	List<User> findByActiveTrueOrderByDisplayNameAsc();

	/** Active users for a role — Story 3.4 uses {@code ADMIN} for report-alert fan-out. */
	List<User> findByRoleAndActiveTrue(User.Role role);
}
