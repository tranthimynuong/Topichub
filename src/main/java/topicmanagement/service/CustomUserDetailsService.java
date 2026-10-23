package topicmanagement.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import topicmanagement.entity.AppUser;
import topicmanagement.repository.UserRepository;

/**
 * CustomUserDetailsService implementing Spring Security's UserDetailsService.
 * Loads user credentials and roles directly from the database (Lab 4 & Chapter 7).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String cleanUsername = username != null ? username.trim() : "";
        AppUser user = userRepository.findByUsernameIgnoreCase(cleanUsername)
                .or(() -> userRepository.findByUsername(cleanUsername))
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + cleanUsername));

        String role = (user.getRole() != null && user.getRole().getCode() != null)
                ? user.getRole().getCode().toUpperCase()
                : "STUDENT";

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .roles(role) // Spring automatically prepends ROLE_ (e.g. ROLE_ADMIN, ROLE_STUDENT)
                .disabled(!Boolean.TRUE.equals(user.getIsActive()))
                .build();
    }
}
