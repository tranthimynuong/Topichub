package topicmanagement.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import topicmanagement.entity.*;
import topicmanagement.service.*;
import topicmanagement.service.EmailService;

import topicmanagement.repository.StudentGroupRepository;
import topicmanagement.repository.SubmissionRepository;

import java.time.OffsetDateTime;
import java.util.*;

@Controller
@RequestMapping("/department-head")
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'ADMIN')")
public class DepartmentHeadController {

    private final DepartmentHeadService departmentHeadService;
    private final CouncilService councilService;
    private final AnnouncementService announcementService;
    private final StudentGroupRepository studentGroupRepository;
    private final SubmissionRepository submissionRepository;
    private final GroupRegistrationService groupRegistrationService;
    private final ExportService exportService;
    private final EmailService emailService;

    public DepartmentHeadController(
            DepartmentHeadService departmentHeadService,
            CouncilService councilService,
            AnnouncementService announcementService,
            StudentGroupRepository studentGroupRepository,
            SubmissionRepository submissionRepository,
            GroupRegistrationService groupRegistrationService,
            ExportService exportService,
            EmailService emailService) {
        this.departmentHeadService = departmentHeadService;
        this.councilService = councilService;
        this.announcementService = announcementService;
        this.studentGroupRepository = studentGroupRepository;
        this.submissionRepository = submissionRepository;
        this.groupRegistrationService = groupRegistrationService;
        this.exportService = exportService;
        this.emailService = emailService;
    }

    private AppUser getAuthenticatedDeptHead(HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("currentUser");
        if (user == null) {
            return null;
        }
        if (user.getRole() == null || (!"DEPARTMENT_HEAD".equalsIgnoreCase(user.getRole().getCode())
                && !"ADMIN".equalsIgnoreCase(user.getRole().getCode()))) {
            return null;
        }
        return user;
    }

    @ModelAttribute("topbarNotifications")
    public List<topicmanagement.dto.TopbarNotificationDto> populateTopbarNotifications(HttpSession session) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) return Collections.emptyList();

        Department dept = departmentHeadService.getDepartmentForUser(user);
        Long deptId = dept != null ? dept.getId() : null;

        List<topicmanagement.dto.TopbarNotificationDto> notifications = new ArrayList<>();

        // 1. Thông báo đề tài mới chờ duyệt từ Giảng viên (Ưu tiên hiển thị cho Trưởng khoa)
        List<Topic> allDeptTopics = departmentHeadService.getDepartmentTopics(deptId, null, null, null);
        List<Topic> pendingTopics = allDeptTopics.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .limit(5)
                .toList();

        for (Topic t : pendingTopics) {
            String authorName = (t.getSupervisor() != null && t.getSupervisor().getUser() != null)
                    ? t.getSupervisor().getUser().getFullName()
                    : "Giảng viên";
            String dateStr = t.getCreatedAt() != null 
                    ? java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(t.getCreatedAt()) 
                    : "Mới";
            topicmanagement.dto.TopbarNotificationDto dto = new topicmanagement.dto.TopbarNotificationDto(
                    "topic-" + t.getId(),
                    "[ĐỀ TÀI MỚI] " + t.getTitle(),
                    "GV " + authorName + " vừa gửi đề xuất đề tài mới cần phê duyệt",
                    "DUYỆT ĐỀ TÀI",
                    authorName,
                    dateStr,
                    "/department-head/topics?status=PENDING",
                    "fa-solid fa-file-circle-question text-xs",
                    "bg-amber-50 text-amber-600"
            );
            notifications.add(dto);
        }

        // 2. Thông báo từ cấp trên / toàn trường (LOẠI BỎ TRIỆT ĐỂ CÁC THÔNG BÁO DO CHÍNH TRƯỞNG KHOA TỰ BAN HÀNH)
        List<Announcement> announcements = deptId != null 
                ? announcementService.getAnnouncementsByDepartment(deptId) 
                : announcementService.getAllAnnouncements();

        for (Announcement a : announcements) {
            // Chặn tuyệt đối: Người tạo thông báo không nhận lại thông báo của chính mình
            if (a.getAuthor() != null && user.getId().equals(a.getAuthor().getId())) {
                continue;
            }

            // Lọc bỏ thông báo nhắc hạn nộp đề xuất đề tài (chỉ dành cho Giảng viên hướng dẫn nộp đề xuất)
            String titleLower = a.getTitle() != null ? a.getTitle().toLowerCase() : "";
            if (titleLower.contains("hạn đề xuất") || titleLower.contains("đến hạn đề xuất") 
                    || titleLower.contains("nộp đề xuất") || titleLower.contains("đã đến hạn")) {
                continue;
            }

            String authorName = a.getAuthor() != null ? a.getAuthor().getFullName() : "Ban Giám Hiệu";
            String dateStr = a.getCreatedAt() != null 
                    ? java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(a.getCreatedAt()) 
                    : "Mới";
            topicmanagement.dto.TopbarNotificationDto dto = new topicmanagement.dto.TopbarNotificationDto(
                    String.valueOf(a.getId()),
                    a.getTitle(),
                    a.getContent(),
                    a.getCategory() != null ? a.getCategory() : "THÔNG BÁO",
                    authorName,
                    dateStr,
                    null,
                    "fa-solid fa-bullhorn text-xs",
                    "bg-blue-50 text-blue-600"
            );
            notifications.add(dto);
        }

        return notifications;
    }

    // ==================== DASHBOARD ====================
    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        List<RegistrationPeriod> allDeptPeriods = departmentHeadService.getAllPeriods(dept != null ? dept.getId() : null);

        Integer currentSemester = (session != null && session.getAttribute("selectedSemester") != null)
                ? (Integer) session.getAttribute("selectedSemester")
                : (allDeptPeriods.isEmpty() ? 1 : (allDeptPeriods.get(0).getSemester() != null ? allDeptPeriods.get(0).getSemester() : 1));
        String currentAcademicYear = (session != null && session.getAttribute("selectedAcademicYear") != null)
                ? (String) session.getAttribute("selectedAcademicYear")
                : (allDeptPeriods.isEmpty() ? "2026-2027" : (allDeptPeriods.get(0).getAcademicYear() != null ? allDeptPeriods.get(0).getAcademicYear() : "2026-2027"));

        Map<String, Object> stats = departmentHeadService.getDepartmentStats(dept != null ? dept.getId() : null, currentSemester, currentAcademicYear);
        List<Topic> rawTopics = departmentHeadService.getDepartmentTopics(dept != null ? dept.getId() : null, null, null, null);

        // Lọc đồng bộ theo học kỳ & năm học đang chọn từ Neon PostgreSQL
        List<Topic> allDeptTopics = rawTopics.stream()
                .filter(t -> {
                    if (currentSemester != null) {
                        Integer tSem = t.getSemester() != null ? t.getSemester() : (t.getPeriod() != null ? t.getPeriod().getSemester() : null);
                        if (tSem != null && !tSem.equals(currentSemester)) return false;
                    }
                    if (currentAcademicYear != null && !currentAcademicYear.isBlank()) {
                        String tYear = t.getAcademicYear() != null ? t.getAcademicYear() : (t.getPeriod() != null ? t.getPeriod().getAcademicYear() : null);
                        if (tYear != null && !tYear.equalsIgnoreCase(currentAcademicYear)) return false;
                    }
                    return true;
                })
                .toList();

        List<Topic> pendingTopics = allDeptTopics.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .toList();

        List<RegistrationPeriod> periods = allDeptPeriods.stream()
                .filter(p -> {
                    if (currentSemester != null && p.getSemester() != null && !p.getSemester().equals(currentSemester)) return false;
                    if (currentAcademicYear != null && p.getAcademicYear() != null && !p.getAcademicYear().equalsIgnoreCase(currentAcademicYear)) return false;
                    return true;
                })
                .toList();
        List<Announcement> announcements = announcementService.getAnnouncementsByDepartment(dept != null ? dept.getId() : null);
        List<Council> councils = councilService.getCouncilsByDepartment(dept != null ? dept.getId() : null);

        long countExcellent = allDeptTopics.stream().filter(t -> t.getFinalScore() != null && t.getFinalScore() >= 9.0).count();
        long countGood = allDeptTopics.stream().filter(t -> t.getFinalScore() != null && t.getFinalScore() >= 8.0 && t.getFinalScore() < 9.0).count();
        long countFair = allDeptTopics.stream().filter(t -> t.getFinalScore() != null && t.getFinalScore() >= 6.5 && t.getFinalScore() < 8.0).count();
        long countAverage = allDeptTopics.stream().filter(t -> t.getFinalScore() != null && t.getFinalScore() >= 5.0 && t.getFinalScore() < 6.5).count();
        long countFailed = allDeptTopics.stream().filter(t -> t.getFinalScore() != null && t.getFinalScore() < 5.0).count();
        long countUngraded = allDeptTopics.stream().filter(t -> t.getFinalScore() == null && "APPROVED".equalsIgnoreCase(t.getStatus())).count();

        long countWithReviewer = allDeptTopics.stream().filter(t -> t.getReviewer() != null).count();
        long countWithCouncil = allDeptTopics.stream().filter(t -> t.getCouncil() != null).count();
        long countFinalized = allDeptTopics.stream().filter(t -> Boolean.TRUE.equals(t.getIsFinalized())).count();

        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("stats", stats);
        model.addAttribute("pendingTopics", pendingTopics.stream().limit(8).toList());
        model.addAttribute("totalPendingTopics", pendingTopics.size());
        model.addAttribute("periods", periods.stream().limit(4).toList());
        model.addAttribute("allPeriods", periods);
        model.addAttribute("councils", councils.stream().limit(4).toList());
        model.addAttribute("announcements", announcements.stream().limit(3).toList());

        model.addAttribute("countExcellent", countExcellent);
        model.addAttribute("countGood", countGood);
        model.addAttribute("countFair", countFair);
        model.addAttribute("countAverage", countAverage);
        model.addAttribute("countFailed", countFailed);
        model.addAttribute("countUngraded", countUngraded);
        model.addAttribute("countWithReviewer", countWithReviewer);
        model.addAttribute("countWithCouncil", countWithCouncil);
        model.addAttribute("countFinalized", countFinalized);

        // Đếm số đề tài chờ chấm điểm hội đồng của Trưởng khoa (nếu có hồ sơ Giảng viên và là thành viên hội đồng)
        int pendingCouncilTopicsCount = councilService.countPendingCouncilGradingForUser(user);
        model.addAttribute("pendingCouncilTopicsCount", pendingCouncilTopicsCount);

        return "department-head/dashboard";
    }

    // ==================== FULL SCHEDULE / CALENDAR ====================

    @GetMapping("/schedule")
    public String showSchedule(HttpSession session, Model model) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        List<RegistrationPeriod> periods = departmentHeadService.getAllPeriods(dept != null ? dept.getId() : null);
        List<Council> councils = dept != null ? councilService.getCouncilsByDepartment(dept.getId()) : councilService.getAllCouncils();
        List<Announcement> announcements = dept != null ? announcementService.getAnnouncementsByDepartment(dept.getId()) : announcementService.getAllAnnouncements();

        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("periods", periods);
        model.addAttribute("allPeriods", periods);
        model.addAttribute("councils", councils);
        model.addAttribute("announcements", announcements.stream().limit(5).toList());

        return "department-head/schedule";
    }

    // ==================== REGISTRATION PERIODS ====================
    @GetMapping("/periods")
    public String listPeriods(HttpSession session, Model model) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        List<RegistrationPeriod> periods = departmentHeadService.getAllPeriods(dept.getId());

        Map<String, Object> periodStats = new HashMap<>();
        long total = periods.size();
        long proposalOpen = periods.stream().filter(p -> "PROPOSAL_OPEN".equalsIgnoreCase(p.getDisplayStatus())).count();
        long registrationOpen = periods.stream().filter(p -> "REGISTRATION_OPEN".equalsIgnoreCase(p.getDisplayStatus())).count();
        long reviewingOrDefense = periods.stream().filter(p -> "REVIEWING".equalsIgnoreCase(p.getDisplayStatus()) || "DEFENSE_WAITING".equalsIgnoreCase(p.getDisplayStatus())).count();
        long closedOrEnded = periods.stream().filter(p -> "CLOSED".equalsIgnoreCase(p.getDisplayStatus()) || "COMPLETED".equalsIgnoreCase(p.getDisplayStatus()) || "REGISTRATION_ENDED".equalsIgnoreCase(p.getDisplayStatus())).count();

        periodStats.put("total", total);
        periodStats.put("proposalOpen", proposalOpen);
        periodStats.put("registrationOpen", registrationOpen);
        periodStats.put("reviewingOrDefense", reviewingOrDefense);
        periodStats.put("closedOrEnded", closedOrEnded);

        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("periods", periods);
        model.addAttribute("periodStats", periodStats);

        return "department-head/periods";
    }

    @PostMapping("/periods/create")
    public Object createPeriod(
            @RequestParam("name") String name,
            @RequestParam("periodType") String periodType,
            @RequestParam("academicYear") String academicYear,
            @RequestParam("semester") Integer semester,
            @RequestParam(value = "minTopicsPerLecturer", defaultValue = "3") Integer minTopicsPerLecturer,
            @RequestParam(value = "propStart", required = false) String propStart,
            @RequestParam(value = "propEnd", required = false) String propEnd,
            @RequestParam(value = "regStart", required = false) String regStart,
            @RequestParam(value = "regEnd", required = false) String regEnd,
            @RequestParam(value = "revDead", required = false) String revDead,
            @RequestParam(value = "defenseDate", required = false) String defenseDate,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));

        if (user == null) {
            if (isAjax) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!"));
            }
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);

        OffsetDateTime pStart = parseDate(propStart, false);
        OffsetDateTime pEnd = parseDate(propEnd, true);
        OffsetDateTime rStart = parseDate(regStart, false);
        OffsetDateTime rEnd = parseDate(regEnd, true);
        OffsetDateTime rDead = parseDate(revDead, true);
        OffsetDateTime dDate = parseDate(defenseDate, false);

        String validationError = null;

        if (pStart != null && pEnd != null && pEnd.toLocalDate().isBefore(pStart.toLocalDate())) {
            validationError = "Hạn chót GV đề xuất phải diễn ra từ ngày hoặc sau ngày bắt đầu đề xuất!";
        } else if (pEnd != null && rStart != null && rStart.toLocalDate().isBefore(pEnd.toLocalDate())) {
            validationError = "Thời gian SV bắt đầu đăng ký phải diễn ra từ ngày hoặc sau khi GV kết thúc hạn đề xuất đề tài!";
        } else if (rStart != null && rEnd != null && rEnd.toLocalDate().isBefore(rStart.toLocalDate())) {
            validationError = "Hạn chót SV đăng ký phải diễn ra từ ngày hoặc sau ngày bắt đầu đăng ký!";
        } else if (rEnd != null && rDead != null && rDead.toLocalDate().isBefore(rEnd.toLocalDate())) {
            validationError = "Hạn nộp điểm GVPB phải diễn ra từ ngày hoặc sau khi kết thúc hạn SV đăng ký đề tài!";
        } else if (rDead != null && dDate != null && dDate.toLocalDate().isBefore(rDead.toLocalDate())) {
            validationError = "Ngày báo cáo Hội đồng phải diễn ra từ ngày hoặc sau hạn nộp điểm GVPB!";
        }

        if (validationError != null) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", validationError));
            }
            redirectAttributes.addFlashAttribute("errorMessage", validationError);
            return "redirect:/department-head/periods";
        }

        // Ràng buộc nghiệp vụ theo Đề bài cuối kỳ:
        // Chỉ đợt loại TLCN hoặc KLTN mới áp dụng Hạn nộp điểm GVPB (reviewerDeadline)
        // Chỉ đợt loại KLTN mới có Ngày bảo vệ Hội đồng chính thức (defenseDate)
        boolean isPeriodWithReviewer = "TLCN".equalsIgnoreCase(periodType) || "KLTN".equalsIgnoreCase(periodType);
        boolean isPeriodWithDefense = "KLTN".equalsIgnoreCase(periodType);
        if (!isPeriodWithReviewer) {
            rDead = null; // MON_HOC và NCKH không có hạn GVPB riêng
        }
        if (!isPeriodWithDefense) {
            dDate = null; // Chỉ KLTN mới có ngày báo cáo Hội đồng bảo vệ
        }

        try {
            RegistrationPeriod savedPeriod = departmentHeadService.createPeriod(dept, name, periodType, academicYear, semester, pStart, pEnd, rStart, rEnd, rDead, dDate, minTopicsPerLecturer);

            // Tự động ban hành Thông báo chính thức cho toàn Khoa / Giảng viên / Sinh viên
            try {
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                StringBuilder sb = new StringBuilder();
                sb.append("Kính gửi toàn thể Giảng viên và Sinh viên thuộc ").append(dept != null ? dept.getName() : "Khoa").append(",\n\n");
                sb.append("Khoa/Bộ môn xin thông báo chính thức ban hành kế hoạch tổ chức: **").append(name).append("** (Học kỳ ").append(semester).append(" - Năm học ").append(academicYear).append(").\n\n");
                sb.append("### 📅 CÁC MỐC THỜI GIAN QUAN TRỌNG:\n");
                if (pStart != null && pEnd != null) {
                    sb.append("- **1. Giảng viên đề xuất đề tài:** từ ").append(pStart.format(dtf)).append(" đến ").append(pEnd.format(dtf))
                            .append(" (Chỉ tiêu tối thiểu: **").append(savedPeriod.getMinTopicsPerLecturer()).append(" đề tài/GV**)\n");
                }
                if (rStart != null && rEnd != null) {
                    sb.append("- **2. Sinh viên đăng ký đề tài:** từ ").append(rStart.format(dtf)).append(" đến ").append(rEnd.format(dtf)).append("\n");
                }
                if (rDead != null) {
                    sb.append("- **3. Hạn nộp điểm GVPB:** ").append(rDead.format(dtf)).append("\n");
                }
                if (dDate != null) {
                    sb.append("- **4. Báo cáo Hội đồng đánh giá:** ").append(dDate.format(dtf)).append("\n");
                }
                sb.append("\nKính đề nghị các Thầy/Cô và các bạn Sinh viên theo dõi sát các mốc thời gian để thực hiện đúng quy định.");

                String noticeTitle = "[THÔNG BÁO BAN HÀNH] Kế hoạch " + name + " (HK" + semester + " " + academicYear + ")";
                announcementService.createAnnouncement(user, dept, savedPeriod.getId(), noticeTitle, sb.toString(), "PERIOD", true, null);
            } catch (Exception e) {
                System.err.println("[ANNOUNCEMENT-AUTO-CREATE-ERROR] " + e.getMessage());
            }

            if (isAjax) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Tạo đợt đăng ký và ban hành thông báo '" + name + "' thành công!"));
            }

            redirectAttributes.addFlashAttribute("successMessage", "Tạo đợt đăng ký và ban hành thông báo '" + name + "' thành công!");
            return "redirect:/department-head/periods";
        } catch (Exception e) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Lỗi tạo đợt đăng ký: " + e.getMessage()));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi tạo đợt đăng ký: " + e.getMessage());
            return "redirect:/department-head/periods";
        }
    }

    @PostMapping("/periods/{id}/status")
    public String updatePeriodStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        // BUG-06 FIX: Truyền departmentId để kiểm tra cross-department
        Department dept = departmentHeadService.getDepartmentForUser(user);
        boolean success = departmentHeadService.updatePeriodStatus(id, status, dept.getId());
        if (success) {
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái đợt thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể cập nhật trạng thái đợt này!");
        }
        return "redirect:/department-head/periods";
    }

    @GetMapping("/periods/{id}/json")
    @ResponseBody
    public ResponseEntity<?> getPeriodJson(@PathVariable("id") Long id, HttpSession session) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Chưa đăng nhập"));
        }
        RegistrationPeriod p = departmentHeadService.getPeriodById(id);
        if (p == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Không tìm thấy đợt"));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", p.getId());
        data.put("name", p.getName());
        data.put("periodType", p.getPeriodType());
        data.put("academicYear", p.getAcademicYear());
        data.put("semester", p.getSemester());
        data.put("minTopicsPerLecturer", p.getMinTopicsPerLecturer());
        data.put("status", p.getStatus() != null ? p.getStatus() : "OPEN");
        data.put("propStart", p.getLecturerProposalStart() != null ? p.getLecturerProposalStart().toLocalDate().toString() : "");
        data.put("propEnd", p.getLecturerProposalEnd() != null ? p.getLecturerProposalEnd().toLocalDate().toString() : "");
        data.put("regStart", p.getStudentRegistrationStart() != null ? p.getStudentRegistrationStart().toLocalDate().toString() : "");
        data.put("regEnd", p.getStudentRegistrationEnd() != null ? p.getStudentRegistrationEnd().toLocalDate().toString() : "");
        data.put("revDead", p.getReviewerDeadline() != null ? p.getReviewerDeadline().toLocalDate().toString() : "");
        data.put("defenseDate", p.getCouncilDefenseDate() != null ? p.getCouncilDefenseDate().toLocalDate().toString() : "");
        return ResponseEntity.ok(data);
    }

    @PostMapping("/periods/{id}/update")
    public Object updatePeriod(
            @PathVariable("id") Long id,
            @RequestParam("name") String name,
            @RequestParam("periodType") String periodType,
            @RequestParam("academicYear") String academicYear,
            @RequestParam("semester") Integer semester,
            @RequestParam(value = "minTopicsPerLecturer", defaultValue = "3") Integer minTopicsPerLecturer,
            @RequestParam(value = "status", defaultValue = "OPEN") String status,
            @RequestParam(value = "propStart", required = false) String propStart,
            @RequestParam(value = "propEnd", required = false) String propEnd,
            @RequestParam(value = "regStart", required = false) String regStart,
            @RequestParam(value = "regEnd", required = false) String regEnd,
            @RequestParam(value = "revDead", required = false) String revDead,
            @RequestParam(value = "defenseDate", required = false) String defenseDate,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));

        if (user == null) {
            if (isAjax) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!"));
            }
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);

        OffsetDateTime pStart = parseDate(propStart, false);
        OffsetDateTime pEnd = parseDate(propEnd, true);
        OffsetDateTime rStart = parseDate(regStart, false);
        OffsetDateTime rEnd = parseDate(regEnd, true);
        OffsetDateTime rDead = parseDate(revDead, true);
        OffsetDateTime dDate = parseDate(defenseDate, false);

        String validationError = null;

        if (pStart != null && pEnd != null && pEnd.toLocalDate().isBefore(pStart.toLocalDate())) {
            validationError = "Hạn chót GV đề xuất phải diễn ra từ ngày hoặc sau ngày bắt đầu đề xuất!";
        } else if (pEnd != null && rStart != null && rStart.toLocalDate().isBefore(pEnd.toLocalDate())) {
            validationError = "Thời gian SV bắt đầu đăng ký phải diễn ra từ ngày hoặc sau khi GV kết thúc hạn đề xuất đề tài!";
        } else if (rStart != null && rEnd != null && rEnd.toLocalDate().isBefore(rStart.toLocalDate())) {
            validationError = "Hạn chót SV đăng ký phải diễn ra từ ngày hoặc sau ngày bắt đầu đăng ký!";
        } else if (rEnd != null && rDead != null && rDead.toLocalDate().isBefore(rEnd.toLocalDate())) {
            validationError = "Hạn nộp điểm GVPB phải diễn ra từ ngày hoặc sau khi kết thúc hạn SV đăng ký đề tài!";
        } else if (rDead != null && dDate != null && dDate.toLocalDate().isBefore(rDead.toLocalDate())) {
            validationError = "Ngày báo cáo Hội đồng phải diễn ra từ ngày hoặc sau hạn nộp điểm GVPB!";
        }

        if (validationError != null) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", validationError));
            }
            redirectAttributes.addFlashAttribute("errorMessage", validationError);
            return "redirect:/department-head/periods";
        }

        // Ràng buộc nghiệp vụ: chỉ TLCN/KLTN mới có reviewerDeadline; chỉ KLTN mới có defenseDate
        if (!"TLCN".equalsIgnoreCase(periodType) && !"KLTN".equalsIgnoreCase(periodType)) {
            rDead = null;
        }
        if (!"KLTN".equalsIgnoreCase(periodType)) {
            dDate = null;
        }

        RegistrationPeriod updated = departmentHeadService.updatePeriod(id, dept.getId(), name, periodType, academicYear, semester,
                pStart, pEnd, rStart, rEnd, rDead, dDate, minTopicsPerLecturer, status);

        if (updated == null) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Không thể cập nhật đợt này!"));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể cập nhật đợt này!");
            return "redirect:/department-head/periods";
        }

        if (isAjax) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Cập nhật đợt đăng ký '" + name + "' thành công!"));
        }

        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật đợt đăng ký '" + name + "' thành công!");
        return "redirect:/department-head/periods";
    }

    @PostMapping("/periods/{id}/delete")
    public String deletePeriod(
            @PathVariable("id") Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        boolean success = departmentHeadService.deletePeriod(id, dept.getId());
        if (success) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa đợt đăng ký thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa đợt này!");
        }
        return "redirect:/department-head/periods";
    }

    // ==================== TOPICS MANAGEMENT ====================
    @GetMapping("/topics")
    public String listTopics(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            HttpSession session,
            Model model) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page - 1), size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        // Nạp toàn bộ đề tài của khoa (để client-side chuyển đổi tab và lọc mượt mà, số liệu đồng bộ chính xác)
        org.springframework.data.domain.Page<Topic> topicsPage = departmentHeadService.getDepartmentTopicsPaged(dept != null ? dept.getId() : null, null, periodId, keyword, pageable);
        List<Topic> topics = topicsPage.getContent();
        List<RegistrationPeriod> periods = departmentHeadService.getAllPeriods(dept != null ? dept.getId() : null);
        List<Lecturer> lecturers = departmentHeadService.getLecturersInDepartment(dept != null ? dept.getId() : null);
        List<Council> councils = councilService.getCouncilsByDepartment(dept != null ? dept.getId() : null);
        Integer currentSemester = (session != null && session.getAttribute("selectedSemester") != null)
                ? (Integer) session.getAttribute("selectedSemester")
                : null;
        String currentAcademicYear = (session != null && session.getAttribute("selectedAcademicYear") != null)
                ? (String) session.getAttribute("selectedAcademicYear")
                : null;
        Map<String, Object> stats = departmentHeadService.getDepartmentStats(dept != null ? dept.getId() : null, currentSemester, currentAcademicYear);

        // Lập bản đồ thành viên từng hội đồng để tự động lọc bỏ hội đồng xung đột với GVHD (batch load)
        List<Long> councilIds = councils.stream().map(Council::getId).toList();
        Map<Long, Set<Long>> councilLecturerIdsMap = councilService.getCouncilLecturerIdsMap(councilIds);

        // Nạp nhóm sinh viên đã gắn đề tài (topicGroupMap) cho drawer chi tiết
        List<Long> topicIds = topics.stream().map(Topic::getId).filter(Objects::nonNull).toList();
        Map<Long, StudentGroup> topicGroupMap = new HashMap<>();
        if (!topicIds.isEmpty()) {
            List<StudentGroup> groups = studentGroupRepository.findByTopicIdIn(topicIds);
            for (StudentGroup g : groups) {
                if (g.getTopic() != null && g.getTopic().getId() != null && !"DISSOLVED".equalsIgnoreCase(g.getStatus())) {
                    topicGroupMap.put(g.getTopic().getId(), g);
                }
            }
        }
        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("topics", topics);
        model.addAttribute("topicsPage", topicsPage);
        model.addAttribute("topicGroupMap", topicGroupMap);
        model.addAttribute("periods", periods);
        model.addAttribute("lecturers", lecturers);
        model.addAttribute("councils", councils);
        model.addAttribute("councilLecturerIdsMap", councilLecturerIdsMap);
        model.addAttribute("selectedStatus", (status != null && !status.isBlank()) ? status.trim().toUpperCase() : "ALL");
        model.addAttribute("selectedPeriodId", periodId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("stats", stats);
        model.addAttribute("pendingCount", stats.get("pendingTopics"));

        return "department-head/topics";
    }

    @PostMapping("/topics/{id}/approve")
    public String approveTopic(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        Topic topic = departmentHeadService.getTopicById(id);
        Long deptId = dept != null ? dept.getId() : null;
        boolean success = departmentHeadService.approveTopic(id, deptId);

        if (success) {
            redirectAttributes.addFlashAttribute("successMessage", "Phê duyệt thành công đề tài: '" + (topic != null ? topic.getTitle() : "") + "'!");
            if (topic != null) {
                try {
                    emailService.sendTopicApprovalEmail(topic, true, null);
                } catch (Exception ignored) {
                }
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể phê duyệt đề tài (không tìm thấy hoặc không thuộc khoa của bạn)!");
        }

        return "redirect:/department-head/topics";
    }

    @PostMapping("/topics/{id}/reject")
    public String rejectTopic(
            @PathVariable("id") Long id,
            @RequestParam("rejectionReason") String rejectionReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        Topic topic = departmentHeadService.getTopicById(id);
        Long deptId = dept != null ? dept.getId() : null;
        String reason = (rejectionReason != null && !rejectionReason.trim().isEmpty()) ? rejectionReason.trim() : "Chưa đạt yêu cầu";
        boolean success = departmentHeadService.rejectTopic(id, reason, deptId);

        if (success) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã từ chối đề tài '" + (topic != null ? topic.getTitle() : "") + "'.");
            if (topic != null) {
                try {
                    emailService.sendTopicApprovalEmail(topic, false, reason);
                } catch (Exception ignored) {
                }
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể từ chối đề tài (không tìm thấy hoặc không thuộc khoa của bạn)!");
        }
        return "redirect:/department-head/topics";
    }

    @PostMapping("/topics/{id}/quick-edit-approve")
    public String quickEditAndApprove(
            @PathVariable("id") Long id,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "requirements", required = false) String requirements,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) return "redirect:/login";

        Department dept = departmentHeadService.getDepartmentForUser(user);
        Long deptId = dept != null ? dept.getId() : null;

        boolean success = departmentHeadService.quickEditAndApproveTopic(id, title, description, requirements, null, deptId);
        if (success) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã hiệu chỉnh và Phê duyệt đề tài thành công để kịp tiến độ công bố!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể hiệu chỉnh đề tài (ngoài thẩm quyền hoặc không tìm thấy đề tài)!");
        }
        return "redirect:/department-head/topics";
    }

    @PostMapping("/topics/{id}/assign-reviewer")
    public String assignReviewer(
            @PathVariable("id") Long topicId,
            @RequestParam("reviewerId") Long reviewerId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        Topic topic = departmentHeadService.getTopicById(topicId);
        if (topic == null || !departmentHeadService.isTopicInScope(topic, dept != null ? dept.getId() : null)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Quyền hạn bị từ chối: Đề tài không thuộc phạm vi quản lý của khoa bạn!");
            return "redirect:/department-head/topics";
        }

        boolean ok = councilService.assignReviewer(user, topicId, reviewerId);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Phân công Giảng viên phản biện thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể phân công GVPB trùng với Giảng viên hướng dẫn!");
        }
        return "redirect:/department-head/topics";
    }

    @PostMapping("/topics/{id}/assign-council")
    public String assignCouncil(
            @PathVariable("id") Long topicId,
            @RequestParam(value = "councilId", required = false) Long councilId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        Topic topic = departmentHeadService.getTopicById(topicId);
        if (topic == null || !departmentHeadService.isTopicInScope(topic, dept != null ? dept.getId() : null)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Quyền hạn bị từ chối: Đề tài không thuộc phạm vi quản lý của khoa bạn!");
            return "redirect:/department-head/topics";
        }

        if (councilId != null) {
            Council council = councilService.getCouncilById(councilId);
            if (council != null && council.getDepartment() != null && dept != null && !council.getDepartment().getId().equals(dept.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Quyền hạn bị từ chối: Hội đồng không thuộc khoa của bạn!");
                return "redirect:/department-head/topics";
            }
        }

        boolean ok = councilService.assignTopicToCouncil(user, topicId, councilId);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Phân công đề tài vào Hội đồng bảo vệ thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Xung đột lợi ích: Không thể gán đề tài vào Hội đồng có chứa Giảng viên hướng dẫn!");
        }
        return "redirect:/department-head/topics";
    }

    // ==================== COUNCILS ====================
    @GetMapping("/councils")
    public String listCouncils(HttpSession session, Model model) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);

        List<Council> councils = (dept != null ? councilService.getCouncilsByDepartment(dept.getId()) : Collections.emptyList());
        List<Lecturer> lecturers = (dept != null ? departmentHeadService.getLecturersInDepartment(dept.getId()) : Collections.emptyList());
        List<RegistrationPeriod> periods = (dept != null)
                ? departmentHeadService.getAllPeriods(dept.getId())
                : Collections.emptyList();

        // Lấy tất cả đề tài đã duyệt thuộc Khoa để phục vụ phân công
        List<Topic> allApprovedTopics = (dept != null ? departmentHeadService.getDepartmentTopics(dept.getId(), "APPROVED", null, null) : Collections.emptyList());

        // Lập bản đồ đề tài đã gán cho từng hội đồng
        Map<Long, List<Topic>> councilTopicsMap = new HashMap<>();
        List<Long> councilIds = councils.stream().map(Council::getId).toList();
        Map<Long, Set<Long>> councilLecturerIdsMap = councilService.getCouncilLecturerIdsMap(councilIds);

        for (Council c : councils) {
            councilTopicsMap.put(c.getId(), councilService.getTopicsByCouncil(c.getId()));
        }

        Map<Long, StudentGroup> approvedTopicsGroupsMap = councilService.getGroupsMapForTopics(allApprovedTopics);

        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("councils", councils);
        model.addAttribute("lecturers", lecturers);
        model.addAttribute("periods", periods);
        model.addAttribute("councilTopicsMap", councilTopicsMap);
        model.addAttribute("councilLecturerIdsMap", councilLecturerIdsMap);
        model.addAttribute("allApprovedTopics", allApprovedTopics);
        model.addAttribute("approvedTopicsGroupsMap", approvedTopicsGroupsMap);

        return "department-head/councils";
    }

    @PostMapping("/councils/create")
    public String createCouncil(
            @RequestParam("name") String name,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "defenseDate", required = false) String defenseDate,
            @RequestParam("presidentId") Long presidentId,
            @RequestParam("secretaryId") Long secretaryId,
            @RequestParam(value = "member1Id", required = false) Long member1Id,
            @RequestParam(value = "member2Id", required = false) Long member2Id,
            @RequestParam(value = "member3Id", required = false) Long member3Id,
            @RequestParam(value = "selectedTopicIds", required = false) List<Long> selectedTopicIds,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        String targetRedirect = (redirectUrl != null && !redirectUrl.trim().isEmpty()) ? redirectUrl : "/department-head/councils";

        Department dept = departmentHeadService.getDepartmentForUser(user);

        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên hội đồng không được để trống!");
            return "redirect:" + targetRedirect;
        }

        List<Long> lecturerIds = new ArrayList<>();
        List<String> roles = new ArrayList<>();

        if (presidentId != null) {
            lecturerIds.add(presidentId);
            roles.add("PRESIDENT");
        }
        if (secretaryId != null) {
            lecturerIds.add(secretaryId);
            roles.add("SECRETARY");
        }
        if (member1Id != null) {
            lecturerIds.add(member1Id);
            roles.add("MEMBER");
        }
        if (member2Id != null) {
            lecturerIds.add(member2Id);
            roles.add("MEMBER");
        }
        if (member3Id != null) {
            lecturerIds.add(member3Id);
            roles.add("MEMBER");
        }

        if (lecturerIds.size() < 3) {
            redirectAttributes.addFlashAttribute("errorMessage", "Hội đồng phải có ít nhất 3 thành viên (Chủ tịch, Thư ký và ít nhất 1 Ủy viên)!");
            return "redirect:" + targetRedirect;
        }

        Set<Long> uniqueLids = new HashSet<>();
        for (Long lid : lecturerIds) {
            if (!uniqueLids.add(lid)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Một giảng viên không thể đảm nhiệm nhiều vị trí trong cùng một Hội đồng!");
                return "redirect:" + targetRedirect;
            }
        }

        try {
            Council created = councilService.createCouncil(user, dept != null ? dept.getId() : null,
                    periodId, code, name, location,
                    parseDate(defenseDate), lecturerIds, roles, selectedTopicIds);
            int assignedCount = (selectedTopicIds != null ? selectedTopicIds.size() : 0);
            String successMsg = "Thành lập Hội đồng '" + name.trim() + "' thành công với " + lecturerIds.size() + " giảng viên";
            if (assignedCount > 0) {
                successMsg += " và đã phân công " + assignedCount + " đề tài/nhóm vào hội đồng!";
            } else {
                successMsg += "!";
            }
            redirectAttributes.addFlashAttribute("successMessage", successMsg);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi khi tạo hội đồng: " + ex.getMessage());
        }

        return "redirect:" + targetRedirect;
    }

    @PostMapping("/councils/{id}/assign-topics-batch")
    public String assignTopicsBatchToCouncil(
            @PathVariable("id") Long councilId,
            @RequestParam(value = "selectedTopicIds", required = false) List<Long> selectedTopicIds,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        if (selectedTopicIds == null || selectedTopicIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn ít nhất một đề tài/nhóm để phân công!");
            return "redirect:/department-head/councils";
        }

        try {
            int count = councilService.assignTopicsToCouncil(user, councilId, selectedTopicIds);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phân công thành công " + count + " đề tài/nhóm vào Hội đồng!");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/department-head/councils";
    }

    @PostMapping("/councils/{id}/assign-topic")
    public String assignTopicToCouncil(
            @PathVariable("id") Long councilId,
            @RequestParam("topicId") Long topicId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        boolean ok = councilService.assignTopicToCouncil(user, topicId, councilId);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã phân công đề tài vào Hội đồng bảo vệ thành công!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Xung đột lợi ích: Không thể gán đề tài vào Hội đồng có chứa Giảng viên hướng dẫn!");
        }
        return "redirect:/department-head/councils";
    }

    @PostMapping("/councils/{id}/unassign-topic")
    public String unassignTopicFromCouncil(
            @PathVariable("id") Long councilId,
            @RequestParam("topicId") Long topicId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Topic topic = departmentHeadService.getTopicById(topicId);
        if (topic == null || topic.getCouncil() == null || !Objects.equals(topic.getCouncil().getId(), councilId)
                || !councilService.assignTopicToCouncil(user, topicId, null)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể hủy phân công: đề tài/hội đồng không thuộc phạm vi quản lý!");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy phân công đề tài khỏi Hội đồng thành công!");
        }
        return "redirect:/department-head/councils";
    }

    // ==================== RESULTS & FINALIZING ====================
    @GetMapping("/results")
    public String listResults(
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            HttpSession session,
            Model model) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        List<Topic> approvedTopics = departmentHeadService.getDepartmentTopics(dept.getId(), "APPROVED", periodId, null);
        Map<Long, StudentGroup> allGroupsMap = councilService.getGroupsMapForTopics(approvedTopics);

        // Hiển thị toàn bộ đề tài đã duyệt thuộc khoa/đợt để theo dõi tiến độ chấm điểm & công bố
        List<Topic> allTopics = approvedTopics;

        List<RegistrationPeriod> periods = departmentHeadService.getAllPeriods(dept.getId());

        // Pagination: Limit to 10 topics per page
        int pageSize = 10;
        int totalTopics = allTopics.size();
        int totalPages = totalTopics > 0 ? (int) Math.ceil((double) totalTopics / pageSize) : 1;
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalTopics);

        List<Topic> pagedTopics = (fromIndex < totalTopics) ? allTopics.subList(fromIndex, toIndex) : Collections.emptyList();

        Map<Long, List<Evaluation>> topicEvaluationsMap = councilService.getEvaluationsMapForTopics(pagedTopics);
        Map<Long, StudentGroup> topicGroupsMap = councilService.getGroupsMapForTopics(pagedTopics);

        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("topics", pagedTopics);
        model.addAttribute("periods", periods);
        model.addAttribute("selectedPeriodId", periodId);
        model.addAttribute("topicEvaluationsMap", topicEvaluationsMap);
        model.addAttribute("topicGroupsMap", topicGroupsMap);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalTopics", totalTopics);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("hasPrevious", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);

        return "department-head/results";
    }

    @PostMapping("/results/{id}/publish")
    public String togglePublishResults(
            @PathVariable("id") Long topicId,
            @RequestParam("isFinalized") Boolean isFinalized,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        Topic topic = departmentHeadService.getTopicById(topicId);
        if (topic == null || !departmentHeadService.isTopicInScope(topic, dept != null ? dept.getId() : null)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Quyền hạn bị từ chối: Đề tài không thuộc phạm vi quản lý của khoa bạn!");
            return "redirect:/department-head/results";
        }

        var result = councilService.publishTopicResults(topicId, isFinalized);
        if (result.published() > 0) {
            redirectAttributes.addFlashAttribute("successMessage", Boolean.TRUE.equals(isFinalized)
                    ? "Đã công bố kết quả. Máy chủ email đã tiếp nhận " + result.sent() + " email."
                    : "Đã chuyển về trạng thái dự thảo điểm.");
            if (result.failed() > 0) redirectAttributes.addFlashAttribute("errorMessage",
                    "Có " + result.failed() + " email chưa gửi được. Kiểm tra SMTP và địa chỉ người nhận; kết quả điểm đã được lưu.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không có thay đổi: kết quả đã ở trạng thái yêu cầu hoặc đề tài chưa đủ phiếu chấm!");
        }
        return "redirect:/department-head/results";
    }

    @PostMapping("/results/publish-all")
    public String publishAllResults(
            @RequestParam(value = "periodId", required = false) String periodIdStr,
            @RequestParam(value = "isFinalized", defaultValue = "true") Boolean isFinalized,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Long periodId = null;
        if (periodIdStr != null && !periodIdStr.trim().isEmpty() && !"null".equalsIgnoreCase(periodIdStr.trim())) {
            try {
                periodId = Long.parseLong(periodIdStr.trim());
            } catch (NumberFormatException ignored) {}
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        var result = councilService.publishAllResults(dept != null ? dept.getId() : null, periodId, isFinalized);
        redirectAttributes.addFlashAttribute("successMessage",
                Boolean.TRUE.equals(isFinalized)
                ? "Đã công bố " + result.published() + " đề tài; máy chủ email đã tiếp nhận " + result.sent() + " email."
                : "Đã chuyển " + result.published() + " đề tài về trạng thái dự thảo điểm.");
        if (result.failed() > 0) redirectAttributes.addFlashAttribute("errorMessage",
                "Có " + result.failed() + " email chưa gửi được. Kiểm tra SMTP và địa chỉ người nhận; kết quả điểm đã được lưu.");
        return "redirect:/department-head/results" + (periodId != null ? "?periodId=" + periodId : "");
    }

    @GetMapping("/results/export-csv")
    public org.springframework.http.ResponseEntity<byte[]> exportResultsCsv(
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        List<Topic> approvedTopics = departmentHeadService.getDepartmentTopics(dept.getId(), "APPROVED", periodId, null);
        Map<Long, StudentGroup> topicGroupsMap = councilService.getGroupsMapForTopics(approvedTopics);
        List<Topic> topics = approvedTopics.stream()
                .filter(t -> topicGroupsMap.containsKey(t.getId()))
                .toList();

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for seamless Excel compatibility
        sb.append('\uFEFF');
        sb.append("STT,Mã Đề Tài,Tên Đề Tài,Khoa Quản Lý,Đợt Đăng Ký,GV Hướng Dẫn,GV Phản Biện,Hội Đồng,Tên Nhóm,Mã SV Nhóm Trưởng,Tên Nhóm Trưởng,Điểm GVHD,Điểm GVPB,Điểm Hội Đồng,Điểm Tổng Kết,Trạng Thái Công Bố\n");

        int index = 1;
        for (Topic t : topics) {
            StudentGroup g = topicGroupsMap.get(t.getId());
            String supervisorName = (t.getSupervisor() != null && t.getSupervisor().getUser() != null) ? t.getSupervisor().getUser().getFullName() : "N/A";
            String reviewerName = (t.getReviewer() != null && t.getReviewer().getUser() != null) ? t.getReviewer().getUser().getFullName() : "N/A";
            String councilName = (t.getCouncil() != null) ? t.getCouncil().getName() : "N/A";
            String groupName = (g != null) ? g.getGroupName() : "Chưa có nhóm";
            String leaderCode = (g != null && g.getLeader() != null) ? g.getLeader().getStudentCode() : "";
            String leaderName = (g != null && g.getLeader() != null && g.getLeader().getUser() != null) ? g.getLeader().getUser().getFullName() : "";

            sb.append(index++).append(",")
                    .append(escapeCsv(t.getCode())).append(",")
                    .append(escapeCsv(t.getTitle())).append(",")
                    .append(escapeCsv(t.getDepartment() != null ? t.getDepartment().getName() : "")).append(",")
                    .append(escapeCsv(t.getPeriod() != null ? t.getPeriod().getName() : "")).append(",")
                    .append(escapeCsv(supervisorName)).append(",")
                    .append(escapeCsv(reviewerName)).append(",")
                    .append(escapeCsv(councilName)).append(",")
                    .append(escapeCsv(groupName)).append(",")
                    .append(escapeCsv(leaderCode)).append(",")
                    .append(escapeCsv(leaderName)).append(",")
                    .append(t.getAdvisorScore() != null ? t.getAdvisorScore() : "").append(",")
                    .append(t.getReviewerScore() != null ? t.getReviewerScore() : "").append(",")
                    .append(t.getCouncilScore() != null ? t.getCouncilScore() : "").append(",")
                    .append(t.getFinalScore() != null ? t.getFinalScore() : "").append(",")
                    .append(Boolean.TRUE.equals(t.getIsFinalized()) ? "ĐÃ CÔNG BỐ" : "DỰ THẢO")
                    .append("\n");
        }

        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "bang_diem_tong_ket_" + System.currentTimeMillis() + ".csv";

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/results/export-excel")
    public org.springframework.http.ResponseEntity<byte[]> exportResultsExcel(
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        RegistrationPeriod period = (periodId != null) ? departmentHeadService.getPeriodById(periodId) : null;
        List<Topic> approvedTopics = departmentHeadService.getDepartmentTopics(dept.getId(), "APPROVED", periodId, null);
        Map<Long, StudentGroup> topicGroupsMap = councilService.getGroupsMapForTopics(approvedTopics);
        List<Topic> topics = approvedTopics.stream()
                .filter(t -> topicGroupsMap.containsKey(t.getId()))
                .toList();

        try {
            byte[] excelBytes = exportService.exportResultsToExcel(topics, topicGroupsMap, dept, period);
            String filename = "bang_diem_tong_ket_" + System.currentTimeMillis() + ".xlsx";

            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/results/export-pdf")
    public org.springframework.http.ResponseEntity<byte[]> exportResultsPdf(
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        RegistrationPeriod period = (periodId != null) ? departmentHeadService.getPeriodById(periodId) : null;
        List<Topic> approvedTopics = departmentHeadService.getDepartmentTopics(dept.getId(), "APPROVED", periodId, null);
        Map<Long, StudentGroup> topicGroupsMap = councilService.getGroupsMapForTopics(approvedTopics);
        List<Topic> topics = approvedTopics.stream()
                .filter(t -> topicGroupsMap.containsKey(t.getId()))
                .toList();

        try {
            byte[] pdfBytes = exportService.exportResultsToPdf(topics, topicGroupsMap, dept, period);
            String filename = "bang_diem_tong_ket_" + System.currentTimeMillis() + ".pdf";

            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    private String escapeCsv(String str) {
        if (str == null) {
            return "\"\"";
        }
        return "\"" + str.replace("\"", "\"\"") + "\"";
    }

    // ==================== ANNOUNCEMENTS ====================
    @GetMapping("/announcements")
    public String listAnnouncements(HttpSession session, Model model) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        List<Announcement> announcements = announcementService.getAnnouncementsByDepartment(dept.getId());
        List<RegistrationPeriod> periods = departmentHeadService.getAllPeriods(dept.getId());

        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("announcements", announcements);
        model.addAttribute("periods", periods);

        return "department-head/announcements";
    }

    @PostMapping("/announcements/create")
    public String createAnnouncement(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "isPinned", required = false) Boolean isPinned,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        announcementService.createAnnouncement(user, dept, periodId, title, content, category, isPinned, null);

        redirectAttributes.addFlashAttribute("successMessage", "Đăng thông báo mới thành công!");
        return "redirect:/department-head/announcements";
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        if (announcementService.deleteAnnouncement(user, id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa thông báo!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa: thông báo không tồn tại hoặc không thuộc khoa quản lý!");
        }
        return "redirect:/department-head/announcements";
    }

    @PostMapping("/announcements/{id}/toggle-pin")
    public String togglePinAnnouncement(@PathVariable("id") Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        boolean ok = announcementService.togglePin(user, id);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã thay đổi trạng thái ghim thông báo!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Thông báo không tồn tại hoặc không thuộc khoa quản lý!");
        }
        return "redirect:/department-head/announcements";
    }

    // ==================== UNIFIED PERSONNEL HUB ====================
    @GetMapping("/personnel")
    public String showPersonnelHub(
            @RequestParam(value = "tab", defaultValue = "lecturers") String tab,
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "8") int size,
            HttpSession session,
            Model model) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        Department dept = departmentHeadService.getDepartmentForUser(user);
        List<Lecturer> lecturers = departmentHeadService.getLecturersInDepartment(dept.getId());
        List<RegistrationPeriod> periods = departmentHeadService.getAllPeriods(dept.getId());

        // Default to first period if none selected
        Long effectivePeriodId = periodId;
        if (effectivePeriodId == null && !periods.isEmpty()) {
            effectivePeriodId = periods.get(0).getId();
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page - 1), size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        org.springframework.data.domain.Page<StudentGroup> groupsPage = departmentHeadService.getGroupsInDepartmentAndPeriodPaged(dept.getId(), effectivePeriodId, pageable);
        List<StudentGroup> pagedGroups = groupsPage.getContent();

        Map<Long, List<Submission>> groupSubmissionsMap = new HashMap<>();
        List<Topic> pagedTopics = new ArrayList<>();
        for (StudentGroup g : pagedGroups) {
            groupSubmissionsMap.put(g.getId(), submissionRepository.findByGroupId(g.getId()));
            if (g.getTopic() != null) {
                pagedTopics.add(g.getTopic());
            }
        }
        Map<Long, List<Evaluation>> topicEvaluationsMap = councilService.getEvaluationsMapForTopics(pagedTopics);

        // Map topic count for each lecturer (in-memory aggregation to avoid N+1 remote DB queries)
        List<Topic> approvedDeptTopics = departmentHeadService.getDepartmentTopics(dept.getId(), "APPROVED", null, null);
        Map<Long, Long> lecturerTopicCountMap = new HashMap<>();
        for (Lecturer lec : lecturers) {
            lecturerTopicCountMap.put(lec.getId(), 0L);
        }
        for (Topic t : approvedDeptTopics) {
            if (t.getSupervisor() != null) {
                lecturerTopicCountMap.merge(t.getSupervisor().getId(), 1L, Long::sum);
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("department", dept);
        model.addAttribute("activeTab", tab);
        model.addAttribute("lecturers", lecturers);
        model.addAttribute("lecturerTopicCountMap", lecturerTopicCountMap);
        model.addAttribute("groups", pagedGroups);
        model.addAttribute("groupsPage", groupsPage);
        model.addAttribute("periods", periods);
        model.addAttribute("selectedPeriodId", effectivePeriodId);
        model.addAttribute("groupSubmissionsMap", groupSubmissionsMap);
        model.addAttribute("topicEvaluationsMap", topicEvaluationsMap);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", groupsPage.getTotalPages());
        model.addAttribute("totalGroups", groupsPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("hasPrevious", groupsPage.hasPrevious());
        model.addAttribute("hasNext", groupsPage.hasNext());

        return "department-head/personnel";
    }

    @GetMapping("/lecturers")
    public String listLecturers() {
        return "redirect:/department-head/personnel?tab=lecturers";
    }

    @GetMapping("/students")
    public String listStudents(@RequestParam(value = "periodId", required = false) Long periodId) {
        return "redirect:/department-head/personnel?tab=students" + (periodId != null ? "&periodId=" + periodId : "");
    }

    @PostMapping("/students/groups/{id}/unassign-topic")
    public String unassignTopicFromGroup(
            @PathVariable("id") Long groupId,
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        StudentGroup group = studentGroupRepository.findById(groupId).orElse(null);
        if (group != null) {
            group.setTopic(null);
            group.setStatus("DRAFT");
            studentGroupRepository.save(group);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy gán đề tài cho nhóm " + group.getGroupName() + "!");
        }
        return "redirect:/department-head/students" + (periodId != null ? "?periodId=" + periodId : "");
    }

    @PostMapping("/groups/merge")
    public String mergeGroups(
            @RequestParam("sourceGroupId") Long sourceGroupId,
            @RequestParam("targetGroupId") Long targetGroupId,
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppUser user = getAuthenticatedDeptHead(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            topicmanagement.dto.RegistrationDtos.ApiResponse<?> res = groupRegistrationService.mergeGroups(user, sourceGroupId, targetGroupId);
            redirectAttributes.addFlashAttribute("successMessage", res.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/department-head/students" + (periodId != null ? "?periodId=" + periodId : "");
    }

    private OffsetDateTime parseDate(String dateStr) {
        return parseDate(dateStr, false);
    }

    private OffsetDateTime parseDate(String dateStr, boolean isEndOfDay) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            if (dateStr.length() == 10) { // yyyy-MM-dd
                String timePart = isEndOfDay ? "T23:59:59+07:00" : "T00:00:00+07:00";
                return OffsetDateTime.parse(dateStr + timePart);
            }
            if (dateStr.length() == 16) { // yyyy-MM-ddTHH:mm
                return OffsetDateTime.parse(dateStr + ":00+07:00");
            }
            return OffsetDateTime.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}
