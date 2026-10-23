package topicmanagement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import topicmanagement.entity.AppUser;
import topicmanagement.repository.UserRepository;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser authenticate(String username, String password) {
        String cleanUsername = username != null ? username.trim() : "";
        String cleanPassword = password != null ? password.trim() : "";

        log.debug("==================================================");
        log.debug("[AUTH DEBUG] Bắt đầu xác thực đăng nhập từ Database:");
        log.debug("  -> Username nhập vào: '{}'", cleanUsername);

        // 1. Tìm kiếm trực tiếp trong Database
        Optional<AppUser> userOpt = userRepository.findByUsernameIgnoreCase(cleanUsername);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(cleanUsername);
        }

        if (userOpt.isEmpty()) {
            log.warn("  -> [AUTH FAILED] KHÔNG tìm thấy tài khoản với username: '{}' trong Database", cleanUsername);
            log.debug("==================================================");
            return null;
        }

        AppUser foundUser = userOpt.get();
        log.debug("  -> [DB] Tìm thấy User: ID={}, Username='{}', FullName='{}', Role={}", 
                foundUser.getId(), foundUser.getUsername(), foundUser.getFullName(), 
                (foundUser.getRole() != null ? foundUser.getRole().getCode() : "null"));

        // 2. Kiểm tra trạng thái hoạt động của tài khoản
        if (foundUser.getIsActive() != null && !foundUser.getIsActive()) {
            log.warn("  -> [AUTH FAILED] Tài khoản đã bị KHÓA (isActive = false)");
            log.debug("==================================================");
            return null;
        }

        // 3. Kiểm tra mật khẩu (hỗ trợ cả Plain Text và BCrypt)
        String storedHash = foundUser.getPasswordHash();

        boolean isMatch = verifyPassword(cleanPassword, storedHash);
        log.debug("  -> Kết quả đối soát mật khẩu: {}", (isMatch ? "CHÍNH XÁC (MATCH)" : "SAI (MISMATCH)"));

        if (isMatch) {
            // Auto-upgrade plaintext passwords to BCrypt
            upgradePasswordIfNeeded(foundUser, cleanPassword);
            log.info("  -> [AUTH SUCCESS] Đăng nhập THÀNH CÔNG cho user: {} (Vai trò: {})", 
                    foundUser.getUsername(), (foundUser.getRole() != null ? foundUser.getRole().getCode() : "Chưa có"));
            log.debug("==================================================");
            return foundUser;
        }

        log.warn("  -> [AUTH FAILED] Mật khẩu không trùng khớp cho user: {}", cleanUsername);
        log.debug("==================================================");
        return null;
    }

    /**
     * Xác minh mật khẩu hỗ trợ BCrypt, plaintext cũ và mật khẩu mặc định mẫu cho dev.
     */
    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null) {
            return false;
        }

        // 1. Kiểm tra BCrypt hash
        if (storedHash != null && (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"))) {
            try {
                if (passwordEncoder.matches(rawPassword, storedHash)) {
                    return true;
                }
            } catch (Exception e) {
                log.error("  -> [BCrypt Error] {}", e.getMessage());
            }
        }

        // 2. So sánh plaintext (cho dữ liệu cũ chưa hash)
        return rawPassword.equals(storedHash);
    }

    /**
     * Tự động chuẩn hóa và lưu lại BCrypt hash chuẩn vào Database sau khi đăng nhập thành công.
     */
    @Transactional
    public void upgradePasswordIfNeeded(AppUser user, String rawPassword) {
        String storedHash = user.getPasswordHash();
        boolean isValidBcrypt = storedHash != null
                && (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"))
                && passwordEncoder.matches(rawPassword, storedHash);

        if (!isValidBcrypt) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
            log.info("  -> [AUTO-UPGRADE] Đã chuẩn hóa và cập nhật BCrypt hash mới vào Database cho user: {}", user.getUsername());
        }
    }

    @Transactional(readOnly = true)
    public AppUser findUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        return userRepository.findByUsernameIgnoreCase(username.trim())
                .or(() -> userRepository.findByUsername(username.trim()))
                .orElse(null);
    }
}
