package com.smcs.user;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmcsUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public SmcsUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("user not found: " + username));
		return new SmcsUserPrincipal(user);
	}

	public static final class SmcsUserPrincipal implements UserDetails {

		private final User user;

		public SmcsUserPrincipal(User user) {
			this.user = user;
		}

		public Long getUserId() {
			return user.getId();
		}

		public User.Role getRole() {
			return user.getRole();
		}

		public String getDisplayName() {
			return user.getDisplayName();
		}

		@Override
		public List<SimpleGrantedAuthority> getAuthorities() {
			return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
		}

		@Override
		public String getPassword() {
			return user.getPasswordHash();
		}

		@Override
		public String getUsername() {
			return user.getUsername();
		}

		@Override
		public boolean isAccountNonExpired() {
			return true;
		}

		@Override
		public boolean isAccountNonLocked() {
			return true;
		}

		@Override
		public boolean isCredentialsNonExpired() {
			return true;
		}

		@Override
		public boolean isEnabled() {
			return user.isActive();
		}
	}
}
