package topicmanagement.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import topicmanagement.entity.AppUser;
import topicmanagement.repository.UserRepository;
import topicmanagement.service.StudentService;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/topics", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
public class TopicApiController {

    private final StudentService studentService;
    private final UserRepository userRepository;

    public TopicApiController(StudentService studentService, UserRepository userRepository) {
        this.studentService = studentService;
        this.userRepository = userRepository;
    }

    private AppUser getAuthenticatedUser(HttpSession session) {
        AppUser user = session != null ? (AppUser) session.getAttribute("currentUser") : null;
        if (user == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                user = userRepository.findByUsernameIgnoreCase(auth.getName()).orElse(null);
                if (user != null && session != null) {
                    session.setAttribute("currentUser", user);
                }
            }
        }
        return user;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getTopicDetail(@PathVariable("id") Long id, HttpSession session) {
        AppUser currentUser = getAuthenticatedUser(session);
        Map<String, Object> data = studentService.getTopicDetailMap(id, currentUser);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }
}
