package topicmanagement.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import topicmanagement.entity.AppUser;
import topicmanagement.service.AuthService;
import topicmanagement.service.GoogleAuthService;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import topicmanagement.service.CustomUserDetailsService;

import java.util.UUID;

@Controller
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final CustomUserDetailsService customUserDetailsService;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService, CustomUserDetailsService customUserDetailsService) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @GetMapping("/login")
    public String showLoginPage(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:/dashboard";
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String handleLogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session,
            Model model) {

        String trimmedUser = username != null ? username.trim() : "";
        String trimmedPass = password != null ? password.trim() : "";

        AppUser user = authService.authenticate(trimmedUser, trimmedPass);
        if (user != null) {
            session.setAttribute("currentUser", user);
            setSecurityContext(user, session);
            return "redirect:/dashboard";
        }

        model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không chính xác!");
        model.addAttribute("username", trimmedUser);
        return "auth/login";
    }

    @GetMapping("/auth/google")
    public String initiateGoogleLogin(HttpSession session, RedirectAttributes redirectAttributes) {
        if (!googleAuthService.isConfigured()) {
            redirectAttributes.addFlashAttribute("error", 
                "Chưa cấu hình Google Client ID / Secret trong hệ thống. Vui lòng cập nhật application.properties hoặc sử dụng phương thức đăng nhập tài khoản.");
            return "redirect:/login?google_unconfigured=true";
        }

        String state = UUID.randomUUID().toString();
        session.setAttribute("oauth_state", state);
        String googleAuthUrl = googleAuthService.buildAuthorizationUrl(state);
        return "redirect:" + googleAuthUrl;
    }

    @GetMapping("/auth/google/callback")
    public String handleGoogleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (error != null) {
            redirectAttributes.addFlashAttribute("error", "Đăng nhập Google không thành công: " + error);
            return "redirect:/login";
        }

        String expectedState = (String) session.getAttribute("oauth_state");
        session.removeAttribute("oauth_state");
        if (expectedState == null || state == null || !expectedState.equals(state)) {
            redirectAttributes.addFlashAttribute("error", "Trạng thái xác thực (state) không hợp lệ hoặc phiên đã hết hạn. Vui lòng thử lại!");
            return "redirect:/login";
        }

        if (code == null || code.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không nhận được mã xác thực từ Google.");
            return "redirect:/login";
        }

        try {
            AppUser user = googleAuthService.processGoogleCallback(code);
            session.setAttribute("currentUser", user);
            setSecurityContext(user, session);
            return "redirect:/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @GetMapping("/logout")
    public String handleLogout(HttpSession session) {
        SecurityContextHolder.clearContext();
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?logout=true";
    }

    @GetMapping("/switch-account")
    public String switchAccount(
            @RequestParam("username") String username,
            @RequestParam(value = "redirect", required = false) String redirectUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = authService.findUserByUsername(username);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy tài khoản: " + username);
            return "redirect:/dashboard";
        }

        session.setAttribute("currentUser", user);
        setSecurityContext(user, session);

        if (redirectUrl != null && !redirectUrl.trim().isEmpty() && !redirectUrl.contains("://")) {
            return "redirect:" + redirectUrl;
        }

        return "redirect:/dashboard";
    }

    private void setSecurityContext(AppUser user, HttpSession session) {
        if (user == null || session == null) return;
        try {
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getUsername());
            Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
        } catch (Exception ignored) {}
    }
}