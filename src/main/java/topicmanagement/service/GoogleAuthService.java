package topicmanagement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import topicmanagement.entity.AppUser;
import topicmanagement.entity.Role;
import topicmanagement.repository.RoleRepository;
import topicmanagement.repository.UserRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class GoogleAuthService {

    @Value("${google.oauth2.client-id:}")
    private String clientId;

    @Value("${google.oauth2.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth2.redirect-uri:http://localhost:8080/auth/google/callback}")
    private String redirectUri;

    @Value("${app.auth.student-email-domains:}")
    private String allowedEmailDomains;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentService studentService;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleAuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentService studentService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentService = studentService;
        this.passwordEncoder = passwordEncoder;
    }


    public boolean isConfigured() {
        return clientId != null && !clientId.trim().isEmpty()
                && !clientId.contains("YOUR_GOOGLE_CLIENT_ID")
                && clientSecret != null && !clientSecret.trim().isEmpty()
                && !clientSecret.contains("YOUR_GOOGLE_CLIENT_SECRET");
    }

    public String buildAuthorizationUrl(String state) {
        String scope = "openid email profile";
        String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(state != null ? state : "", StandardCharsets.UTF_8);

        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + encodedRedirectUri
                + "&response_type=code"
                + "&scope=" + encodedScope
                + "&prompt=select_account"
                + "&state=" + encodedState;
    }

    @Transactional
    public AppUser processGoogleCallback(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã xác thực Google (authorization code) không hợp lệ.");
        }

        // 1. Gửi request đổi code lấy access token
        String tokenUrl = "https://oauth2.googleapis.com/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject(tokenUrl, requestEntity, Map.class);
            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new IllegalStateException("Không nhận được token từ Google.");
            }

            String accessToken = (String) tokenResponse.get("access_token");

            // 2. Lấy thông tin người dùng từ Google UserInfo API
            String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(accessToken);
            HttpEntity<Void> userInfoEntity = new HttpEntity<>(authHeaders);

            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = restTemplate.exchange(userInfoUrl, HttpMethod.GET, userInfoEntity, Map.class).getBody();
            if (userInfo == null || !userInfo.containsKey("email")) {
                throw new IllegalStateException("Không lấy được thông tin email từ tài khoản Google.");
            }

            String email = ((String) userInfo.get("email")).toLowerCase().trim();
            String name = (String) userInfo.get("name");

            return processStudentLoginByEmail(email, name);

        } catch (Exception e) {
            System.err.println("[GOOGLE AUTH ERROR] " + e.getMessage());
            throw new RuntimeException("Lỗi trong quá trình xác thực Google: " + e.getMessage(), e);
        }
    }

    @Transactional
    public AppUser processStudentLoginByEmail(String email, String fullName) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống.");
        }

        email = email.toLowerCase().trim();

        // 1. Kiểm tra domain email nếu có cấu hình
        if (allowedEmailDomains != null && !allowedEmailDomains.trim().isEmpty()) {
            String[] allowed = allowedEmailDomains.split(",");
            boolean isAllowed = false;
            for (String domain : allowed) {
                String d = domain.trim().toLowerCase();
                if (!d.isEmpty() && (email.endsWith("@" + d) || email.endsWith("." + d) || d.equals("*"))) {
                    isAllowed = true;
                    break;
                }
            }
            if (!isAllowed) {
                throw new IllegalArgumentException("Email " + email + " không thuộc danh sách tên miền được phép đăng nhập (" + allowedEmailDomains + "). Vui lòng sử dụng email sinh viên trường.");
            }
        }

        // 2. Tìm AppUser theo email trong DB
        Optional<AppUser> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Email Google '" + email + "' chưa được liên kết với bất kỳ tài khoản sinh viên nào trong hệ thống! Vui lòng liên hệ Quản trị viên để cập nhật email hồ sơ trước khi đăng nhập bằng Google.");
        }

        AppUser user = userOpt.get();
        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new IllegalStateException("Tài khoản liên kết với email " + email + " đã bị vô hiệu hóa.");
        }

        return user;
    }
}
