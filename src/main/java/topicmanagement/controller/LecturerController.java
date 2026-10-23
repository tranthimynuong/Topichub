package topicmanagement.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import topicmanagement.entity.*;
import topicmanagement.service.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/lecturer")
@PreAuthorize("hasAnyRole('LECTURER', 'DEPARTMENT_HEAD', 'ADMIN')")
public class LecturerController {

    private final LecturerService lecturerService;
    private final CouncilService councilService;
    private final AnnouncementService announcementService;
    private final FileStorageService fileStorageService;
    private final topicmanagement.repository.SubmissionRepository submissionRepository;
    private final GroupRegistrationService groupRegistrationService;

    public LecturerController(
            LecturerService lecturerService,
            CouncilService councilService,
            AnnouncementService announcementService,
            FileStorageService fileStorageService,
            topicmanagement.repository.SubmissionRepository submissionRepository,
            GroupRegistrationService groupRegistrationService) {
        this.lecturerService = lecturerService;
        this.councilService = councilService;
        this.announcementService = announcementService;
        this.fileStorageService = fileStorageService;
        this.submissionRepository = submissionRepository;
        this.groupRegistrationService = groupRegistrationService;
    }

    /**
     * BUG-04 FIX: Kiểm tra role LECTURER / DEPARTMENT_HEAD / ADMIN
     * (Trưởng khoa và Admin cũng có thể là giảng viên nên được phép truy cập)
     */
    private AppUser getAuthenticatedUser(HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("currentUser");
        if (user == null) return null;
        if (user.getRole() == null) return null;
        String roleCode = user.getRole().getCode();
        if (!"LECTURER".equalsIgnoreCase(roleCode)
                && !"DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)
                && !"ADMIN".equalsIgnoreCase(roleCode)) {
            return null;
        }
        return user;
    }

    /**
     * Đồng bộ hóa danh sách thông báo trên toàn bộ các trang của Giảng viên (Topbar badge luôn nhất quán)
     */
    @ModelAttribute("announcements")
    public List<Announcement> populateAnnouncements(HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return Collections.emptyList();
        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        Long deptId = (lecturer != null && lecturer.getDepartment() != null) ? lecturer.getDepartment().getId() : null;
        return deptId != null 
                ? announcementService.getAnnouncementsForDepartment(deptId) 
                : announcementService.getAllAnnouncements();
    }

    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<Topic> topics = lecturerService.getTopicsByLecturer(lecturer);
        List<Topic> reviewerTopics = lecturerService.getReviewerTopics(lecturer);
        List<Council> councils = lecturerService.getCouncilsForLecturer(lecturer);
        List<StudentGroup> groups = lecturerService.getGroupsForLecturerTopics(lecturer);
        List<Submission> submissions = lecturerService.getSubmissionsByLecturer(lecturer);
        List<topicmanagement.dto.RegistrationDtos.LeaveRequestDto> pendingLeaveRequests = lecturer != null ? groupRegistrationService.getPendingLeaveRequestsForLecturer(user) : List.of();
        List<topicmanagement.dto.RegistrationDtos.DissolveRequestDto> pendingDissolveRequests = lecturer != null ? groupRegistrationService.getPendingDissolveRequestsForLecturer(user) : List.of();

        long approvedCount = topics.stream().filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus())).count();
        long pendingCount = topics.stream().filter(t -> "PENDING".equalsIgnoreCase(t.getStatus())).count();
        Long deptId = (lecturer != null && lecturer.getDepartment() != null) ? lecturer.getDepartment().getId() : null;
        List<Announcement> announcements = announcementService.getAnnouncementsForDepartment(deptId);

        List<StudentGroup> pendingGroups = groups.stream()
                .filter(g -> "DRAFT".equalsIgnoreCase(g.getStatus()) || "REGISTERED".equalsIgnoreCase(g.getStatus()) || "PENDING".equalsIgnoreCase(g.getStatus()))
                .toList();
        List<Submission> ungradedSubmissions = submissions.stream()
                .filter(s -> s.getGrade() == null)
                .toList();
        List<Topic> rejectedTopics = topics.stream()
                .filter(t -> "REJECTED".equalsIgnoreCase(t.getStatus()))
                .toList();

        List<RegistrationPeriod> openProposalPeriods = lecturerService.getOpenProposalPeriods(lecturer);
        List<LecturerService.ProposalProgressDto> proposalProgressList = lecturerService.getProposalProgressList(lecturer);

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("openProposalPeriods", openProposalPeriods);
        model.addAttribute("proposalProgressList", proposalProgressList);
        model.addAttribute("topics", topics);
        model.addAttribute("reviewerTopics", reviewerTopics);
        model.addAttribute("councils", councils);
        model.addAttribute("groups", groups);
        model.addAttribute("studentGroups", groups);
        model.addAttribute("pendingGroups", pendingGroups);
        model.addAttribute("ungradedSubmissions", ungradedSubmissions);
        model.addAttribute("rejectedTopics", rejectedTopics);
        model.addAttribute("submissions", submissions);
        model.addAttribute("totalSubmissions", submissions.size());
        model.addAttribute("totalTopics", topics.size());
        model.addAttribute("approvedTopics", approvedCount);
        model.addAttribute("pendingTopics", pendingCount);
        model.addAttribute("reviewerCount", reviewerTopics.size());
        model.addAttribute("councilCount", councils.size());
        model.addAttribute("pendingLeaveRequests", pendingLeaveRequests);
        model.addAttribute("pendingDissolveRequests", pendingDissolveRequests);

        return "lecturer/dashboard";
    }

    // ==================== FULL SCHEDULE / CALENDAR ====================

    @GetMapping("/schedule")
    public String showSchedule(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<Topic> topics = lecturerService.getTopicsByLecturer(lecturer);
        List<Topic> reviewerTopics = lecturerService.getReviewerTopics(lecturer);
        List<Council> councils = lecturerService.getCouncilsForLecturer(lecturer);
        Long deptId = (lecturer != null && lecturer.getDepartment() != null) ? lecturer.getDepartment().getId() : null;
        List<RegistrationPeriod> periods = lecturerService.getPeriodsForLecturer(lecturer);
        List<Submission> submissions = lecturerService.getSubmissionsByLecturer(lecturer);

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("topics", topics);
        model.addAttribute("reviewerTopics", reviewerTopics);
        model.addAttribute("councils", councils);
        model.addAttribute("periods", periods);
        model.addAttribute("submissions", submissions);

        return "lecturer/schedule";
    }

    // ==================== TOPICS ====================

    @GetMapping("/topics")
    public String listTopics(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<Topic> topics = lecturerService.getTopicsByLecturer(lecturer);
        List<RegistrationPeriod> periods = lecturerService.getPeriodsForLecturer(lecturer);

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("topics", topics);
        model.addAttribute("periods", periods);

        return "lecturer/topics";
    }

    @GetMapping("/topics/create")
    public String showCreateTopicForm(
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "periodName", required = false) String periodName,
            @RequestParam(value = "noticeId", required = false) Long noticeId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "requirements", required = false) String requirements,
            @RequestParam(value = "refNoticeTitle", required = false) String refNoticeTitle,
            HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<RegistrationPeriod> openPeriods = new java.util.ArrayList<>(lecturerService.getOpenProposalPeriods(lecturer));
        List<LecturerService.ProposalProgressDto> proposalProgressList = lecturerService.getProposalProgressList(lecturer);
        List<Lecturer> coSupervisors = lecturerService.getOtherLecturers(lecturer.getId());

        // 1. Determine selected Period
        Long selectedPeriodId = periodId;

        // 1a. If noticeId is provided, check if the announcement was linked to a period
        if (selectedPeriodId == null && noticeId != null) {
            Announcement notice = announcementService.getAnnouncementById(noticeId);
            if (notice != null && notice.getPeriod() != null) {
                selectedPeriodId = notice.getPeriod().getId();
            }
        }

        // 1b. If periodName is provided, match against openPeriods (exact or partial)
        if (selectedPeriodId == null && periodName != null && !periodName.trim().isEmpty()) {
            String pNameLower = periodName.trim().toLowerCase();
            selectedPeriodId = openPeriods.stream()
                    .filter(p -> p.getName() != null && (
                            p.getName().trim().equalsIgnoreCase(periodName.trim()) ||
                            pNameLower.contains(p.getName().trim().toLowerCase()) ||
                            p.getName().trim().toLowerCase().contains(pNameLower)
                    ))
                    .map(RegistrationPeriod::getId)
                    .findFirst()
                    .orElse(null);
        }

        // 1c. If still not matched, check if refNoticeTitle matches any period's name, semester, or academic year
        if (selectedPeriodId == null && refNoticeTitle != null && !refNoticeTitle.trim().isEmpty()) {
            String noticeTitleLower = refNoticeTitle.trim().toLowerCase();
            selectedPeriodId = openPeriods.stream()
                    .filter(p -> {
                        if (p.getName() != null && noticeTitleLower.contains(p.getName().trim().toLowerCase())) {
                            return true;
                        }
                        if (p.getAcademicYear() != null && noticeTitleLower.contains(p.getAcademicYear().toLowerCase())) {
                            if (p.getSemester() != null && noticeTitleLower.contains("hk" + p.getSemester())) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .map(RegistrationPeriod::getId)
                    .findFirst()
                    .orElse(null);
        }

        // 1d. If selectedPeriodId is determined but not currently in openPeriods, fetch from all periods and add to openPeriods
        if (selectedPeriodId != null) {
            final Long targetPeriodId = selectedPeriodId;
            boolean alreadyInList = openPeriods.stream().anyMatch(p -> p.getId() != null && p.getId().equals(targetPeriodId));
            if (!alreadyInList) {
                lecturerService.getAllPeriods().stream()
                        .filter(p -> p.getId() != null && p.getId().equals(targetPeriodId))
                        .findFirst()
                        .ifPresent(p -> openPeriods.add(0, p));
            }
        }

        // 1e. Fallback: if still null, default to the single open period or the active open period
        if (selectedPeriodId == null && !openPeriods.isEmpty()) {
            selectedPeriodId = openPeriods.get(0).getId();
        }

        if (selectedPeriodId != null) {
            model.addAttribute("selectedPeriodId", selectedPeriodId);
        }

        // 2. Pre-fill Topic template / title / description / requirements if requested from quick proposal
        Topic topic = new Topic();
        if (title != null && !title.trim().isEmpty()) {
            topic.setTitle(title.trim());
        } else if (refNoticeTitle != null && !refNoticeTitle.trim().isEmpty()) {
            String cleanTitle = refNoticeTitle.trim()
                    .replaceAll("(?i)^\\[.*?\\]\\s*", "")
                    .replaceAll("(?i)^(Thông báo|Kế hoạch|Mở cổng|Đăng ký)\\s*:?\\s*", "")
                    .trim();
            // Chỉ điền tên đề tài nếu thông báo không phải là thông báo ban hành kế hoạch đợt chung
            boolean isGenericPeriodNotice = cleanTitle.toLowerCase().contains("kế hoạch")
                    || cleanTitle.matches(".*\\d{2}/\\d{2}/\\d{4}.*")
                    || cleanTitle.matches(".*\\bhk[1-3]\\b.*")
                    || cleanTitle.isEmpty();
            if (!isGenericPeriodNotice) {
                topic.setTitle("Đề tài: " + cleanTitle);
            }
        }

        if (description != null && !description.trim().isEmpty()) {
            topic.setDescription(description.trim());
        }
        if (requirements != null && !requirements.trim().isEmpty()) {
            topic.setRequirements(requirements.trim());
        }

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("topic", topic);
        model.addAttribute("openPeriods", openPeriods);
        model.addAttribute("proposalProgressList", proposalProgressList);
        model.addAttribute("coSupervisors", coSupervisors);

        return "lecturer/create-topic";
    }

    @PostMapping("/topics/create")
    public String handleCreateTopic(
            @ModelAttribute Topic topic,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "coSupervisorId", required = false) Long coSupervisorId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        try {
            Topic created = lecturerService.createTopic(lecturer, periodId, coSupervisorId, topic);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi đề xuất đề tài '" + created.getTitle() + "' thành công! Vui lòng chờ Trưởng khoa xét duyệt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi đề xuất đề tài: " + e.getMessage());
        }
        return "redirect:/lecturer/topics";
    }

    @GetMapping("/topics/{id}/edit")
    public String showEditTopicForm(@PathVariable("id") Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        Topic topic = lecturerService.getTopicById(id);
        if (topic == null || !topic.isSupervisorOrCoSupervisor(lecturer)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy đề tài hoặc bạn không có quyền chỉnh sửa!");
            return "redirect:/lecturer/topics";
        }

        List<RegistrationPeriod> periods = lecturerService.getAllPeriods();
        List<Lecturer> coSupervisors = lecturerService.getOtherLecturers(lecturer.getId());

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("topic", topic);
        model.addAttribute("periods", periods);
        model.addAttribute("coSupervisors", coSupervisors);

        return "lecturer/edit-topic";
    }

    @PostMapping("/topics/{id}/edit")
    public String handleEditTopic(
            @PathVariable("id") Long id,
            @ModelAttribute Topic topic,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "coSupervisorId", required = false) Long coSupervisorId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        try {
            Topic updated = lecturerService.updateTopic(lecturer, id, periodId, coSupervisorId, topic);
            if ("PENDING".equalsIgnoreCase(updated.getStatus())) {
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật đề tài '" + updated.getTitle() + "' thành công! Đề tài hiện ở trạng thái Chờ duyệt (PENDING) để Trưởng khoa thẩm định.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật đề tài '" + updated.getTitle() + "' thành công!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi cập nhật đề tài: " + e.getMessage());
        }
        return "redirect:/lecturer/topics";
    }

    @PostMapping("/topics/{id}/delete")
    public String handleDeleteTopic(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        try {
            boolean success = lecturerService.deleteTopic(id, lecturer);
            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa đề tài thành công!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa đề tài này!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa: " + e.getMessage());
        }
        return "redirect:/lecturer/topics";
    }

    // ==================== GROUPS & APPROVAL ====================

    @GetMapping("/registrations")
    public String listGroups(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<StudentGroup> groups = lecturerService.getGroupsForLecturerTopics(lecturer);
        List<RegistrationPeriod> periods = lecturerService.getAllPeriods();

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("groups", groups);
        model.addAttribute("periods", periods);

        return "lecturer/registrations";
    }

    @PostMapping("/registrations/{id}/approve")
    public String approveGroup(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        // BUG-07 FIX: Truyền lecturer để kiểm tra ownership
        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        try {
            boolean success = lecturerService.approveGroupTopic(id, "APPROVED", lecturer);
            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt nhóm sinh viên hướng dẫn thành công!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể phê duyệt nhóm này (không tìm thấy hoặc không phải đề tài bạn hướng dẫn)!");
            }
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/lecturer/registrations";
    }

    @PostMapping("/registrations/{id}/reject")
    public String rejectGroup(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        // BUG-07 FIX: Truyền lecturer để kiểm tra ownership
        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        boolean success = lecturerService.approveGroupTopic(id, "REJECTED", lecturer);
        if (success) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã từ chối nhóm đăng ký.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể từ chối nhóm này!");
        }
        return "redirect:/lecturer/registrations";
    }

    @PostMapping("/topics/{topicId}/grade-advisor")
    public String gradeAdvisor(
            @PathVariable("topicId") Long topicId,
            @RequestParam("score") Double score,
            @RequestParam(value = "comments", required = false) String comments,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        if (councilService.isTopicFinalized(topicId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đề tài đã công bố kết quả điểm số chính thức cho sinh viên! Điểm số đã bị khóa, yêu cầu Trưởng bộ môn/Khoa thu hồi công bố về trạng thái dự thảo trước khi chỉnh sửa.");
            return "redirect:/lecturer/registrations";
        }

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        boolean ok = councilService.submitEvaluation(topicId, lecturer.getId(), "SUPERVISOR", score, comments);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã nộp điểm và nhận xét hướng dẫn (quá trình) thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể nộp điểm hướng dẫn (bạn không phải GVHD hoặc đề tài chưa có nhóm sinh viên)!");
        }
        return "redirect:/lecturer/registrations";
    }

    // ==================== REVIEWS (GVPB) ====================

    @GetMapping("/reviews")
    public String listReviewTopics(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<Topic> reviewerTopics = lecturerService.getReviewerTopics(lecturer);
        List<RegistrationPeriod> periods = lecturerService.getAllPeriods();

        Map<Long, Evaluation> existingEvaluationsMap = new HashMap<>();
        for (Topic t : reviewerTopics) {
            councilService.getEvaluation(t.getId(), lecturer.getId(), "REVIEWER")
                    .ifPresent(e -> existingEvaluationsMap.put(t.getId(), e));
        }

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("reviewerTopics", reviewerTopics);
        model.addAttribute("existingEvaluationsMap", existingEvaluationsMap);
        model.addAttribute("periods", periods);

        return "lecturer/reviews";
    }

    @PostMapping("/reviews/{topicId}/grade")
    public String gradeReviewer(
            @PathVariable("topicId") Long topicId,
            @RequestParam("score") Double score,
            @RequestParam("comments") String comments,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        if (councilService.isTopicFinalized(topicId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đề tài đã công bố kết quả điểm số chính thức cho sinh viên! Điểm số đã bị khóa, yêu cầu Trưởng bộ môn/Khoa thu hồi công bố về trạng thái dự thảo trước khi chỉnh sửa.");
            return "redirect:/lecturer/reviews";
        }

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        boolean ok = councilService.submitEvaluation(topicId, lecturer.getId(), "REVIEWER", score, comments);

        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã nộp điểm và nhận xét phản biện thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể chấm phản biện: kiểm tra phân công, thời hạn, trạng thái khóa và điểm hợp lệ!");
        }

        return "redirect:/lecturer/reviews";
    }

    // ==================== COUNCILS ====================

    @GetMapping("/councils")
    public String listCouncils(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<Council> councils = lecturerService.getCouncilsForLecturer(lecturer);
        List<RegistrationPeriod> periods = lecturerService.getAllPeriods();

        Map<Long, List<Topic>> councilTopicsMap = new HashMap<>();
        Map<Long, Evaluation> existingEvaluationsMap = new HashMap<>();
        for (Council c : councils) {
            List<Topic> topics = councilService.getTopicsInCouncil(c.getId());
            councilTopicsMap.put(c.getId(), topics);
            for (Topic t : topics) {
                councilService.getEvaluation(t.getId(), lecturer.getId(), "COUNCIL")
                        .ifPresent(e -> existingEvaluationsMap.put(t.getId(), e));
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("councils", councils);
        model.addAttribute("councilTopicsMap", councilTopicsMap);
        model.addAttribute("existingEvaluationsMap", existingEvaluationsMap);
        model.addAttribute("periods", periods);

        return "lecturer/councils";
    }

    @PostMapping("/councils/{topicId}/grade")
    public String gradeCouncil(
            @PathVariable("topicId") Long topicId,
            @RequestParam("score") Double score,
            @RequestParam("comments") String comments,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        if (councilService.isTopicFinalized(topicId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đề tài đã công bố kết quả điểm số chính thức cho sinh viên! Điểm số đã bị khóa, yêu cầu Trưởng bộ môn/Khoa thu hồi công bố về trạng thái dự thảo trước khi chỉnh sửa.");
            return "redirect:/lecturer/councils";
        }

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        boolean ok = councilService.submitEvaluation(topicId, lecturer.getId(), "COUNCIL", score, comments);

        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã nộp phiếu chấm điểm Hội đồng thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "LỖI: Không thể chấm điểm đề tài (xung đột lợi ích GVHD hoặc đề tài đã khóa điểm)!");
        }

        return "redirect:/lecturer/councils";
    }

    // ==================== SUBMISSIONS ====================

    @GetMapping("/submissions")
    public String listSubmissions(HttpSession session, Model model) {
        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        List<Submission> submissions = lecturerService.getSubmissionsByLecturer(lecturer);
        List<RegistrationPeriod> periods = lecturerService.getAllPeriods();

        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("submissions", submissions);
        model.addAttribute("periods", periods);

        return "lecturer/submissions";
    }

    @PostMapping("/submissions/{id}/grade")
    public String gradeSubmission(
            @PathVariable("id") Long id,
            @RequestParam("grade") Double grade,
            @RequestParam("feedback") String feedback,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        // BUG-08 FIX: Truyền lecturer để kiểm tra ownership
        Lecturer lecturer = lecturerService.getLecturerByUser(user);
        boolean success = lecturerService.gradeSubmission(id, grade, feedback, lecturer);
        if (success) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi đánh giá bài nộp báo cáo thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể chấm bài nộp này (không tìm thấy hoặc không phải đề tài bạn hướng dẫn)!");
        }
        return "redirect:/lecturer/submissions";
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

        // BẢO MẬT PHÂN QUYỀN (IDOR): Chỉ GVHD, GVPB, Thành viên Hội đồng, Trưởng khoa hoặc Admin mới được tải
        boolean isAdminOrHead = user.getRole() != null && ("ADMIN".equalsIgnoreCase(user.getRole().getCode())
                || "DEPARTMENT_HEAD".equalsIgnoreCase(user.getRole().getCode()));
        if (!isAdminOrHead) {
            Lecturer lecturer = lecturerService.getLecturerByUser(user);
            if (lecturer == null) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            Topic topic = submission.getTopic();
            if (topic == null) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            boolean isSupervisor = topic.isSupervisorOrCoSupervisor(lecturer);
            boolean isReviewer = topic.getReviewer() != null && topic.getReviewer().getId().equals(lecturer.getId());
            boolean isCouncilMember = topic.getCouncil() != null && councilService.getCouncilMembers(topic.getCouncil().getId())
                    .stream().anyMatch(m -> m.getLecturer() != null && m.getLecturer().getId().equals(lecturer.getId()));

            if (!isSupervisor && !isReviewer && !isCouncilMember) {
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

    // ==================== V2 GROUP REQUEST APPROVALS ====================

    @PostMapping("/leave-requests/{id}/approve")
    public String approveLeaveRequest(
            @PathVariable("id") Long requestId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        try {
            topicmanagement.dto.RegistrationDtos.ApiResponse<?> res = groupRegistrationService.approveLeaveRequest(user, requestId);
            redirectAttributes.addFlashAttribute("successMessage", res.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/lecturer/dashboard";
    }

    @PostMapping("/leave-requests/{id}/reject")
    public String rejectLeaveRequest(
            @PathVariable("id") Long requestId,
            @RequestParam(value = "rejectionReason", required = false) String rejectionReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        try {
            groupRegistrationService.rejectLeaveRequest(user, requestId, rejectionReason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu rút nhóm.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/lecturer/dashboard";
    }

    @PostMapping("/dissolve-requests/{id}/approve")
    public String approveDissolveRequest(
            @PathVariable("id") Long requestId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        try {
            topicmanagement.dto.RegistrationDtos.ApiResponse<?> res = groupRegistrationService.approveDissolveRequest(user, requestId);
            redirectAttributes.addFlashAttribute("successMessage", res.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/lecturer/dashboard";
    }

    @PostMapping("/dissolve-requests/{id}/reject")
    public String rejectDissolveRequest(
            @PathVariable("id") Long requestId,
            @RequestParam(value = "rejectionReason", required = false) String rejectionReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";

        try {
            groupRegistrationService.rejectDissolveRequest(user, requestId, rejectionReason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu hủy nhóm và đề tài.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/lecturer/dashboard";
    }
}
