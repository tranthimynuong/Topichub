package topicmanagement.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import topicmanagement.entity.*;
import topicmanagement.service.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
public class StudentController {

    private final StudentService studentService;
    private final AnnouncementService announcementService;
    private final FileStorageService fileStorageService;
    private final topicmanagement.repository.SubmissionRepository submissionRepository;
    private final GroupRegistrationService groupRegistrationService;
    private final topicmanagement.repository.UserRepository userRepository;

    public StudentController(
            StudentService studentService,
            AnnouncementService announcementService,
            FileStorageService fileStorageService,
            topicmanagement.repository.SubmissionRepository submissionRepository,
            GroupRegistrationService groupRegistrationService,
            topicmanagement.repository.UserRepository userRepository) {
        this.studentService = studentService;
        this.announcementService = announcementService;
        this.fileStorageService = fileStorageService;
        this.submissionRepository = submissionRepository;
        this.groupRegistrationService = groupRegistrationService;
        this.userRepository = userRepository;
    }

    /**
     * Kiểm tra phiên đăng nhập và xác thực tài khoản còn tồn tại trong DB
     */
    private AppUser getAuthenticatedUser(HttpSession session) {
        AppUser user = (session != null) ? (AppUser) session.getAttribute("currentUser") : null;
        if (user == null || user.getId() == null) {
            return null;
        }

        var dbUserOpt = userRepository.findById(user.getId());
        if (dbUserOpt.isEmpty() || !Boolean.TRUE.equals(dbUserOpt.get().getIsActive())) {
            if (session != null) {
                session.invalidate();
            }
            return null;
        }

        user = dbUserOpt.get();
        if (session != null) {
            session.setAttribute("currentUser", user);
        }

        if (user.getRole() == null || !"STUDENT".equalsIgnoreCase(user.getRole().getCode())) {
            return null;
        }
        return user;
    }

    /**
     * Đồng bộ hóa danh sách thông báo trên toàn bộ các trang của Sinh viên
     * (Topbar badge luôn nhất quán)
     */
    @ModelAttribute("announcements")
    public List<Announcement> populateAnnouncements(HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return Collections.emptyList();
        }
        Student student = studentService.getStudentByUser(user);
        Long deptId = (student != null && student.getDepartment() != null) ? student.getDepartment().getId() : null;
        return deptId != null
                ? announcementService.getAnnouncementsForDepartment(deptId)
                : announcementService.getAllAnnouncements();
    }

    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, null);
        List<Submission> submissions = studentService.getGroupSubmissions(group);
        List<topicmanagement.dto.RegistrationDtos.InvitationResponseDto> myInvitations = groupRegistrationService.getMyPendingInvitations(user);
        List<RegistrationPeriod> activePeriods = studentService.getActivePeriodsForStudent(student);

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("group", group);
        model.addAttribute("submissions", submissions);
        model.addAttribute("myInvitations", myInvitations);
        model.addAttribute("activePeriods", activePeriods);

        return "student/dashboard";
    }

    // ==================== FULL SCHEDULE / CALENDAR ====================
    @GetMapping("/schedule")
    public String showSchedule(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, null);
        List<RegistrationPeriod> activePeriods = studentService.getActivePeriodsForStudent(student);
        List<Submission> submissions = studentService.getGroupSubmissions(group);

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("group", group);
        model.addAttribute("activePeriods", activePeriods);
        model.addAttribute("submissions", submissions);

        return "student/schedule";
    }

    // ==================== GROUP MANAGEMENT ====================
    @GetMapping("/group")
    public String showGroupPage(
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session,
            Model model) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, periodId);
        List<RegistrationPeriod> activePeriods = studentService.getActivePeriodsForStudent(student);
        List<topicmanagement.dto.RegistrationDtos.InvitationResponseDto> myInvitations = groupRegistrationService.getMyPendingInvitations(user);
        List<topicmanagement.dto.RegistrationDtos.InvitationResponseDto> sentInvitations = group != null ? groupRegistrationService.getGroupInvitations(user, group.getId()) : List.of();

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("group", group);
        model.addAttribute("activePeriods", activePeriods);
        model.addAttribute("selectedPeriodId", periodId);
        model.addAttribute("myInvitations", myInvitations);
        model.addAttribute("sentInvitations", sentInvitations);

        return "student/group";
    }

    @PostMapping("/group/create")
    public String createGroup(
            @RequestParam("groupName") String groupName,
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        try {
            StudentGroup g = studentService.createGroup(student, periodId, groupName);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo nhóm '" + g.getGroupName() + "' thành công! Mã nhóm: " + g.getGroupCode());
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            redirectAttributes.addFlashAttribute("errorMessage", rse.getReason() != null ? rse.getReason() : rse.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/invite")
    public String inviteMember(
            @RequestParam("studentCode") String studentCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, null);

        if (group == null || !group.isLeader(student)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ Nhóm trưởng mới có quyền thêm thành viên vào nhóm!");
            return "redirect:/student/group";
        }

        try {
            String error = studentService.inviteMemberByCode(group, studentCode);
            if (error != null) {
                redirectAttributes.addFlashAttribute("errorMessage", error);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm thành viên " + studentCode.trim() + " vào nhóm thành công!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/join")
    public String joinGroupByCode(
            @RequestParam("groupCode") String groupCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        if (student == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy hồ sơ sinh viên của bạn!");
            return "redirect:/student/group";
        }

        try {
            String error = studentService.joinGroupByCode(student, groupCode);
            if (error != null) {
                redirectAttributes.addFlashAttribute("errorMessage", error);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Gia nhập nhóm thành công!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/remove-member")
    public String removeMember(
            @RequestParam("memberStudentId") Long memberStudentId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, null);

        if (group == null || !group.isLeader(student)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ Nhóm trưởng mới có quyền xóa thành viên!");
            return "redirect:/student/group";
        }

        try {
            boolean success = studentService.removeMember(group, memberStudentId);
            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa thành viên khỏi nhóm thành công!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa thành viên này (hoặc nhóm đã đăng ký đề tài)!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/invitations/{id}/accept")
    public String acceptInvitation(
            @PathVariable("id") Long invitationId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.acceptInvitation(user, invitationId);
            redirectAttributes.addFlashAttribute("successMessage", "Bạn đã gia nhập nhóm thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/invitations/{id}/reject")
    public String rejectInvitation(
            @PathVariable("id") Long invitationId,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.rejectInvitation(user, invitationId, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối lời mời vào nhóm.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/invitations/{id}/cancel")
    public String cancelInvitation(
            @PathVariable("id") Long invitationId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.cancelInvitation(user, invitationId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thu hồi lời mời thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/draft-leave")
    public String leaveDraftGroup(
            @RequestParam("groupId") Long groupId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.leaveGroupDraft(user, groupId, null);
            redirectAttributes.addFlashAttribute("successMessage", "Bạn đã rời nhóm thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/draft-dissolve")
    public String dissolveDraftGroup(
            @RequestParam("groupId") Long groupId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.dissolveGroupDraft(user, groupId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã giải tán nhóm thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/transfer-leadership")
    public String transferLeadership(
            @RequestParam("groupId") Long groupId,
            @RequestParam("newLeaderStudentId") Long newLeaderStudentId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.transferLeadership(user, groupId, newLeaderStudentId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển giao quyền Nhóm trưởng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/leave-request")
    public String submitLeaveRequest(
            @RequestParam("groupId") Long groupId,
            @RequestParam("reason") String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.requestLeaveApprovedGroup(user, groupId, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi yêu cầu rút khỏi nhóm lên GVHD! Vui lòng chờ GVHD phê duyệt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    @PostMapping("/group/dissolve-request")
    public String submitDissolveRequest(
            @RequestParam("groupId") Long groupId,
            @RequestParam("reason") String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            groupRegistrationService.requestDissolveApprovedGroup(user, groupId, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi yêu cầu hủy nhóm và đề tài lên GVHD! Vui lòng chờ GVHD phê duyệt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/student/group";
    }

    // ==================== TOPICS & REGISTRATION ====================
    @GetMapping("/topics")
    public String listTopics(
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpSession session,
            Model model) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        // Xác định Khoa của sinh viên để lọc đề tài theo đúng phân quyền đào tạo
        Long departmentId = student != null ? student.getDepartmentId() : null;
        Department studentDepartment = student != null ? student.getDepartment() : null;

        StudentGroup group = studentService.getMyActiveGroup(student, periodId);
        // Lọc đề tài theo Khoa của sinh viên (departmentId) + periodId + keyword
        List<Topic> topics = studentService.getAvailableTopics(periodId, keyword, departmentId);
        List<RegistrationPeriod> periods = studentService.getActivePeriodsForStudent(student);

        // Lấy danh sách ID đề tài đã có nhóm đăng ký (để làm mờ/disable nút trên UI)
        java.util.Set<Long> takenTopicIds = studentService.getTakenTopicIds(topics);

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("studentDepartment", studentDepartment);
        model.addAttribute("group", group);
        model.addAttribute("topics", topics);
        model.addAttribute("takenTopicIds", takenTopicIds);
        model.addAttribute("periods", periods);
        model.addAttribute("selectedPeriodId", periodId);
        model.addAttribute("keyword", keyword);

        return "student/topics";
    }

    @PostMapping("/topics/{id}/register")
    public String registerTopic(
            @PathVariable("id") Long topicId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        Topic topic = studentService.getTopicById(topicId);
        Long topicPeriodId = (topic != null && topic.getPeriod() != null) ? topic.getPeriod().getId() : null;
        StudentGroup group = studentService.getMyActiveGroup(student, topicPeriodId);
        if (group == null) {
            group = studentService.getMyActiveGroup(student, null);
        }

        String err = studentService.registerTopicForGroup(student, group, topicId);
        if (err != null) {
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return "redirect:/student/topics";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Đăng ký đề tài cho nhóm thành công! Vui lòng chờ Giảng viên hướng dẫn duyệt.");
        return "redirect:/student/my-topic";
    }

    // ==================== MY TOPIC ====================
    @GetMapping("/my-topic")
    public String showMyTopic(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, null);
        List<Submission> submissions = studentService.getGroupSubmissions(group);

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("group", group);
        model.addAttribute("submissions", submissions);

        return "student/my-topic";
    }

    // ==================== SUBMISSIONS (LEADER ONLY) ====================
    @GetMapping("/submissions")
    public String listSubmissions(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, null);
        List<Submission> submissions = studentService.getGroupSubmissions(group);

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("group", group);
        model.addAttribute("submissions", submissions);

        return "student/submissions";
    }

    @PostMapping("/submissions/upload")
    public String handleUploadReport(
            @RequestParam("title") String title,
            @RequestParam("round") Integer round,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        StudentGroup group = studentService.getMyActiveGroup(student, null);

        if (group == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn chưa thuộc nhóm nào để nộp báo cáo!");
            return "redirect:/student/submissions";
        }
        if (!group.isLeader(student)) {
            redirectAttributes.addFlashAttribute("errorMessage", "QUYỀN HẠN BỊ TỪ CHỐI: Chỉ Nhóm trưởng mới được nộp báo cáo!");
            return "redirect:/student/submissions";
        }

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đính kèm file báo cáo thực tế trước khi bấm nộp bài!");
            return "redirect:/student/submissions";
        }

        String err = studentService.submitGroupReport(student, group, title, round, file);

        if (err != null) {
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return "redirect:/student/submissions";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Nhóm trưởng đã nộp file báo cáo tiến độ thành công!");
        return "redirect:/student/submissions";
    }

    @GetMapping("/submissions/{id}/download")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadSubmission(
            @PathVariable("id") Long submissionId,
            HttpSession session) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null || submission.getFilePath() == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        // BẢO MẬT PHÂN QUYỀN (IDOR): Sinh viên chỉ được tải báo cáo thuộc nhóm của mình
        boolean isAdmin = user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getCode());
        if (!isAdmin) {
            Student student = studentService.getStudentByUser(user);
            if (student == null) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            StudentGroup subGroup = submission.getGroup();
            if (subGroup == null || !subGroup.hasMember(student)) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
        }

        try {
            org.springframework.core.io.Resource resource = fileStorageService.loadFileAsResource(submission.getFilePath());
            String contentType = submission.getContentType() != null ? submission.getContentType() : "application/octet-stream";
            String downloadName = submission.getOriginalFileName() != null ? submission.getOriginalFileName() : "submission_" + submissionId + ".pdf";

            return org.springframework.http.ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                    .body(resource);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }

    // ==================== RESULTS ====================
    @GetMapping("/results")
    public String showResults(
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Student student = studentService.getStudentByUser(user);
        if (student == null) {
            return "redirect:/login";
        }

        List<RegistrationPeriod> participatedPeriods = studentService.getParticipatedPeriods(student);
        Long selectedPeriodId = periodId;
        StudentGroup group = null;
        boolean accessDeniedPeriod = false;

        if (periodId != null) {
            final Long targetPeriodId = periodId;
            boolean isAllowed = participatedPeriods.stream()
                    .anyMatch(p -> Objects.equals(p.getId(), targetPeriodId));
            if (!isAllowed) {
                accessDeniedPeriod = true;
            } else {
                group = studentService.getMyActiveGroup(student, targetPeriodId);
            }
        } else {
            if (!participatedPeriods.isEmpty()) {
                selectedPeriodId = participatedPeriods.get(0).getId();
                group = studentService.getMyActiveGroup(student, selectedPeriodId);
            }
        }

        List<Evaluation> evaluations = studentService.getGroupEvaluations(group, student, selectedPeriodId);

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("group", group);
        model.addAttribute("evaluations", evaluations);
        model.addAttribute("participatedPeriods", participatedPeriods);
        model.addAttribute("selectedPeriodId", selectedPeriodId);
        model.addAttribute("accessDeniedPeriod", accessDeniedPeriod);

        return "student/results";
    }
}
