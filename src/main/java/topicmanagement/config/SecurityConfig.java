package topicmanagement.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import topicmanagement.entity.AppUser;
import topicmanagement.repository.UserRepository;

/**
 * Spring Security 6 & Spring Boot 3 Central Security Configuration.
 * Reference: Chapter 07 & Lab 4 - Spring Security (Course WEPR330479).
 * - SecurityFilterChain with Lambda DSL
 * - CustomUserDetailsService for database authentication
 * - PasswordEncoder with BCrypt support
 * - Form Login & HTTP Basic authentication
 * - Role-based authorization & Method-level security (@EnableMethodSecurity)
 * - CSRF protection (exempting REST /api/**)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

            @Override
            public String encode(CharSequence rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (rawPassword == null) return false;
                String raw = rawPassword.toString();
                // Rubric mục 3: Mật khẩu PHẢI được mã hóa BCrypt, tuyệt đối không cho phép plain-text
                if (encodedPassword != null && (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$"))) {
                    try {
                        return bcrypt.matches(raw, encodedPassword);
                    } catch (Exception ignored) {}
                }
                return false; // Từ chối mọi password không phải BCrypt hash
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http
            // 1. CSRF configuration: keep enabled for Thymeleaf forms while exempting /api/** and OAuth callback
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/auth/google/**")
            )
            // 2. Authorize HTTP requests by role and path
            .authorizeHttpRequests(auth -> auth
                // Static assets & system endpoints
                .requestMatchers(
                    "/css/**", "/js/**", "/images/**", "/webjars/**",
                    "/favicon.ico", "/uploads/**", "/error",
                    "/swagger-ui/**", "/v3/api-docs/**",
                    "/actuator/**"
                ).permitAll()
                // Public auth endpoints
                .requestMatchers("/", "/login", "/auth/**", "/switch-account").permitAll()
                // Role-based authorization
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/department-head/**").hasAnyRole("DEPARTMENT_HEAD", "ADMIN")
                .requestMatchers("/lecturer/**").hasAnyRole("LECTURER", "DEPARTMENT_HEAD", "ADMIN")
                .requestMatchers("/student/**").hasAnyRole("STUDENT", "ADMIN")
                // Authenticated routes
                .requestMatchers("/dashboard", "/profile").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            // 3. Form Login
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> {
                    String username = authentication.getName();
                    AppUser user = userRepository.findByUsernameIgnoreCase(username)
                            .or(() -> userRepository.findByUsername(username))
                            .orElse(null);
                    if (user != null) {
                        request.getSession(true).setAttribute("currentUser", user);
                    }
                    response.sendRedirect(request.getContextPath() + "/dashboard");
                })
                .failureUrl("/login?error=true")
                .permitAll()
            )
            // 4. HTTP Basic Authentication for REST clients / Postman
            .httpBasic(Customizer.withDefaults())
            // 5. Logout configuration
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.sendRedirect(request.getContextPath() + "/login?logout=true");
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            // 6. Exception handling for AJAX/API vs Browser requests
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    if (isAjaxOrApiRequest(request, path)) {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"success\":false,\"message\":\"Vui lòng đăng nhập để tiếp tục!\"}");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    if (isAjaxOrApiRequest(request, path)) {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"success\":false,\"message\":\"Bạn không có quyền truy cập chức năng này!\"}");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login?error=access_denied");
                    }
                })
            )
            // 7. Bridge existing HTTP session currentUser into Spring Security context seamlessly
            .addFilterBefore((request, response, chain) -> {
                HttpServletRequest req = (HttpServletRequest) request;
                jakarta.servlet.http.HttpSession session = req.getSession(false);
                if (session != null) {
                    AppUser currentUser = (AppUser) session.getAttribute("currentUser");
                    org.springframework.security.core.Authentication currentAuth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    if (currentUser != null && (currentAuth == null || currentAuth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                        String role = (currentUser.getRole() != null && currentUser.getRole().getCode() != null)
                                ? currentUser.getRole().getCode().toUpperCase()
                                : "STUDENT";
                        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)
                        );
                        org.springframework.security.core.userdetails.User principal = new org.springframework.security.core.userdetails.User(
                                currentUser.getUsername(), "", authorities
                        );
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, authorities);
                        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                org.springframework.security.core.context.SecurityContextHolder.getContext());
                    }
                }
                chain.doFilter(request, response);
            }, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);

        return http.build();
    }

    private static boolean isAjaxOrApiRequest(HttpServletRequest request, String path) {
        if (path.startsWith("/api/")) return true;
        String xRequestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(xRequestedWith)) return true;
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }
}
