package topicmanagement.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import topicmanagement.entity.AppUser;
import topicmanagement.service.AiAssistantService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/ai", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "message", required = false) String paramMessage,
            HttpSession session) {

        AppUser user = (AppUser) session.getAttribute("currentUser");
        if (user == null) {
            user = (AppUser) session.getAttribute("authenticatedUser");
        }
        String message = (body != null && body.containsKey("message")) ? body.get("message") : paramMessage;

        Map<String, Object> res = new HashMap<>();
        try {
            AiAssistantService.AiResponse response = aiAssistantService.processQuery(user, message);
            res.put("status", "success");
            res.put("reply", response != null ? response.reply() : "Xin chào! Tôi có thể giúp gì cho bạn?");
            res.put("suggestions", response != null ? response.suggestions() : java.util.List.of());
        } catch (Exception e) {
            e.printStackTrace();
            res.put("status", "error");
            res.put("reply", "Xin lỗi, đã xảy ra lỗi trong quá trình xử lý câu hỏi của bạn. Vui lòng thử lại với câu hỏi khác!");
            res.put("suggestions", java.util.List.of("Quy chế tính điểm", "Hạn chót các đợt", "Tìm đề tài còn trống"));
        }

        return ResponseEntity.ok(res);
    }
}
