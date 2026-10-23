package topicmanagement.service;

import org.springframework.stereotype.Service;
import topicmanagement.entity.*;
import topicmanagement.repository.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiAssistantService {

    private final TopicRepository topicRepository;
    private final StudentGroupRepository groupRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final SubmissionRepository submissionRepository;
    private final CouncilRepository councilRepository;
    private final DepartmentRepository departmentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record AiResponse(String reply, List<String> suggestions) {}

    public AiAssistantService(
            TopicRepository topicRepository,
            StudentGroupRepository groupRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            RegistrationPeriodRepository periodRepository,
            SubmissionRepository submissionRepository,
            CouncilRepository councilRepository,
            DepartmentRepository departmentRepository) {
        this.topicRepository = topicRepository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.periodRepository = periodRepository;
        this.submissionRepository = submissionRepository;
        this.councilRepository = councilRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Bộ xử lý NLP trung tâm - Phân tích ngữ nghĩa đa chiều, bóc tách thực thể và tra cứu DB thời gian thực.
     */
    public AiResponse processQuery(AppUser user, String query) {
        if (query == null || query.isBlank()) {
            return new AiResponse("Chào bạn! Tôi là Trợ lý AI của TopicHub. Tôi có thể giúp gì cho bạn hôm nay?",
                    getDefaultSuggestions(user));
        }

        String q = query.trim().toLowerCase();
        String roleCode = user != null && user.getRole() != null ? user.getRole().getCode() : "STUDENT";

        // 1. Phân công GVPB / Giảng viên rảnh / Tải công việc giảng viên
        if (q.contains("rảnh") || q.contains("phân gvpb") || q.contains("gán gvpb") || q.contains("phân công phản biện")
                || q.contains("gán phản biện") || q.contains("ít đề tài") || q.contains("chưa có gvpb")
                || q.contains("chưa có phản biện") || (q.contains("tải") && q.contains("giảng viên"))) {
            return handleLecturerWorkloadAndReviewerQuery(user, roleCode);
        }

        // 2. Tra cứu giảng viên cụ thể theo tên (ví dụ: "thầy hướng dẫn 1", "cô hương", "thầy tuấn", "giảng viên phan...")
        if (isQueryingSpecificLecturer(q)) {
            AiResponse specificLecturerResp = handleSpecificLecturerQuery(q);
            if (specificLecturerResp != null) return specificLecturerResp;
        }

        // 3. Quy định & Công thức tính điểm / Độ lệch điểm hội đồng
        if (q.contains("tính điểm") || q.contains("công thức") || q.contains("thang điểm") || q.contains("trọng số")
                || q.contains("quy chế") || q.contains("độ lệch") || q.contains("lệch điểm") || q.contains("lệch 2.0")
                || q.contains("xung đột lợi ích") || q.contains("điểm tổng kết") || q.contains("điểm hội đồng")) {
            return handleGradingFormulaQuery(q);
        }

        // 4. Điều kiện bảo vệ KLTN & Điều kiện qua môn / đạt đề tài
        if (q.contains("điều kiện") || q.contains("được bảo vệ") || q.contains("qua môn") || q.contains("đạt đề tài")
                || q.contains("trượt") || q.contains("đậu")) {
            return handleDefenseEligibilityQuery();
        }

        // 5. Quy định về Nhóm sinh viên & Quyền nộp bài báo cáo
        if (q.contains("ai được nộp") || q.contains("quyền nộp") || q.contains("không nộp được") || q.contains("tại sao không nộp")
                || q.contains("nhóm tối đa") || q.contains("bao nhiêu thành viên") || q.contains("mời thành viên")
                || q.contains("tạo nhóm") || q.contains("đổi trưởng nhóm") || q.contains("rút khỏi nhóm")) {
            return handleGroupRulesAndSubmissionPermissionsQuery(user);
        }

        // 6. Tra cứu Tiến độ & Báo cáo của Nhóm hiện tại
        if (q.contains("tiến độ") || q.contains("báo cáo") || q.contains("nộp bài") || q.contains("nhóm của tôi")
                || q.contains("bài nộp") || q.contains("vòng") || q.contains("điểm của tôi") || q.contains("nhóm")) {
            return handleProgressAndSubmissionQuery(user, roleCode, q);
        }

        // 7. Tra cứu Khoa quản lý đào tạo
        if (q.contains("khoa") || q.contains("đơn vị") || q.contains("bộ môn") || q.contains("chuyên ngành")) {
            return handleDepartmentQuery(q);
        }

        // 8. Tra cứu Đề tài còn trống & Đăng ký đề tài
        if (q.contains("đề tài") || q.contains("tìm đề tài") || q.contains("chỗ trống") || q.contains("đăng ký đề tài")
                || q.contains("danh sách đề tài") || q.contains("chưa ai đăng ký") || q.contains("còn trống")) {
            return handleTopicQuery(user, roleCode, q);
        }

        // 9. Tra cứu Hạn chót, Mốc thời gian & Đợt đăng ký
        if (q.contains("hạn chót") || q.contains("thời hạn") || q.contains("deadline") || q.contains("lịch")
                || q.contains("đợt đăng ký") || q.contains("khi nào hết hạn") || q.contains("bảo vệ khi nào")) {
            return handlePeriodAndDeadlineQuery();
        }

        // 10. Tra cứu Hội đồng bảo vệ
        if (q.contains("hội đồng") || q.contains("chủ tịch") || q.contains("thư ký") || q.contains("ủy viên")
                || q.contains("phòng bảo vệ") || q.contains("lịch bảo vệ")) {
            return handleCouncilQuery(user, roleCode);
        }

        // 11. Hướng dẫn sử dụng các tính năng giao diện (Dark Mode, Xuất PDF, Biểu đồ Chart.js)
        if (q.contains("dark mode") || q.contains("chế độ tối") || q.contains("giao diện tối") || q.contains("xuất pdf")
                || q.contains("in phiếu") || q.contains("in biên bản") || q.contains("biểu đồ") || q.contains("chart")) {
            return handleFeatureHowToQuery(q);
        }

        // 12. Thống kê tổng quan (Dành cho Trưởng khoa / Admin / Giảng viên)
        if (q.contains("thống kê") || q.contains("bao nhiêu") || q.contains("tổng số") || q.contains("chờ duyệt") || q.contains("số liệu")) {
            return handleStatisticsQuery(user, roleCode);
        }

        // 13. Lời chào & Trợ giúp chung
        if (q.contains("chào") || q.contains("hello") || q.contains("hi") || q.contains("giúp") || q.contains("hướng dẫn") || q.contains("bạn là ai")) {
            return handleGreetingQuery(user);
        }

        // Fallback: Tìm kiếm thông minh theo từ khóa trong Đề tài & Giảng viên & Khoa
        return handleSmartSearchFallback(query, user);
    }

    private boolean isQueryingSpecificLecturer(String q) {
        return (q.contains("thầy") || q.contains("cô") || q.contains("giảng viên") || q.contains("ts.") || q.contains("ths.") || q.contains("gv"))
                && (q.contains("hướng dẫn") || q.contains("phản biện") || q.contains("email") || q.contains("số điện thoại")
                || q.contains("đề tài của") || q.contains("ở đâu") || q.contains("khoa nào") || q.matches(".*(thầy|cô|gv)\\s+[a-zA-Z0-9_\\p{L}]+.*"));
    }

    /**
     * Tra cứu thông tin chi tiết một Giảng viên theo tên hoặc học vị.
     */
    private AiResponse handleSpecificLecturerQuery(String q) {
        List<Lecturer> lecturers = lecturerRepository.findAll();
        Lecturer matched = null;

        for (Lecturer l : lecturers) {
            if (l.getUser() == null) continue;
            String fullName = l.getUser().getFullName().toLowerCase();
            String username = l.getUser().getUsername().toLowerCase();

            // Tách các từ trong họ tên để tìm khớp
            String[] nameParts = fullName.split("\\s+");
            String lastName = nameParts[nameParts.length - 1]; // Tên chính (ví dụ: "Dẫn 1", "Biện 3", "Anh")

            if (q.contains(fullName) || q.contains(username) || (q.contains(lastName) && (q.contains("thầy") || q.contains("cô") || q.contains("gv")))) {
                matched = l;
                break;
            }
        }

        if (matched == null) return null;

        String fullName = matched.getUser().getFullName();
        String divName = matched.getDepartment() != null ? matched.getDepartment().getName() : "Khoa CNTT";
        String email = matched.getUser().getEmail() != null ? matched.getUser().getEmail() : "Chưa cập nhật";
        String phone = matched.getUser().getPhone() != null ? matched.getUser().getPhone() : "Chưa cập nhật";

        final Long lecturerId = matched.getId();
        List<Topic> supervisedTopics = topicRepository.findBySupervisorId(lecturerId);
        List<Topic> reviewedTopics = topicRepository.findAll().stream()
                .filter(t -> t.getReviewer() != null && t.getReviewer().getId().equals(lecturerId))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("👨‍🏫 **THÔNG TIN GIẢNG VIÊN: ").append(fullName).append("**\n\n");
        sb.append("* 🏢 **Bộ môn**: ").append(divName).append("\n");
        sb.append("* 📧 **Email**: `").append(email).append("` | 📞 **SĐT**: ").append(phone).append("\n");
        sb.append("* 📋 **Tải công việc hiện tại**:\n");
        sb.append("  * Đang Hướng dẫn: **").append(supervisedTopics.size()).append(" đề tài**.\n");
        sb.append("  * Đang Phản biện: **").append(reviewedTopics.size()).append(" đề tài**.\n\n");

        if (!supervisedTopics.isEmpty()) {
            sb.append("🌟 **Các đề tài đang hướng dẫn:**\n");
            for (Topic t : supervisedTopics.stream().limit(3).toList()) {
                sb.append("  - [`").append(t.getCode()).append("`] *").append(t.getTitle()).append("* (").append(t.getStatus()).append(")\n");
            }
        }

        return new AiResponse(sb.toString(), List.of("Giảng viên nào đang rảnh để phân GVPB", "Đề tài còn trống", "Quy chế tính điểm"));
    }

    /**
     * Trả lời chính xác và trực diện câu hỏi: Giảng viên nào đang rảnh / ít tải nhất để phân công GVPB.
     */
    private AiResponse handleLecturerWorkloadAndReviewerQuery(AppUser user, String roleCode) {
        List<Lecturer> lecturers = lecturerRepository.findAll();
        List<Topic> allTopics = topicRepository.findAll();

        Map<Long, Long> supervisorCounts = allTopics.stream()
                .filter(t -> t.getSupervisor() != null)
                .collect(Collectors.groupingBy(t -> t.getSupervisor().getId(), Collectors.counting()));

        Map<Long, Long> reviewerCounts = allTopics.stream()
                .filter(t -> t.getReviewer() != null)
                .collect(Collectors.groupingBy(t -> t.getReviewer().getId(), Collectors.counting()));

        List<Topic> topicsNeedingReviewer = allTopics.stream()
                .filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus()) && t.getReviewer() == null)
                .toList();

        List<Lecturer> validLecturers = lecturers.stream()
                .filter(l -> l.getUser() != null && !l.getUser().getUsername().startsWith("user_sec_"))
                .sorted(Comparator.comparingLong(l -> reviewerCounts.getOrDefault(l.getId(), 0L) + supervisorCounts.getOrDefault(l.getId(), 0L)))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("👨‍🏫 **ĐỀ XUẤT PHÂN CÔNG GIẢNG VIÊN PHẢN BIỆN (GVPB):**\n\n");

        if (topicsNeedingReviewer.isEmpty()) {
            sb.append("✅ **Tất cả các đề tài đã được phân công GVPB đầy đủ!**\n\n");
        } else {
            sb.append("⚠️ Hiện có **").append(topicsNeedingReviewer.size()).append(" đề tài** đã duyệt nhưng **chưa gán GVPB**.\n\n");
        }

        sb.append("📊 **Bảng tải công việc (Xếp theo giảng viên đang rảnh nhất):**\n");
        for (Lecturer l : validLecturers) {
            long sup = supervisorCounts.getOrDefault(l.getId(), 0L);
            long rev = reviewerCounts.getOrDefault(l.getId(), 0L);
            String name = l.getUser().getFullName();
            String div = l.getDepartment() != null ? l.getDepartment().getName() : "Khoa CNTT";

            sb.append("* **").append(name).append("** (").append(div).append("):\n");
            sb.append("  ➔ Đang phản biện: **").append(rev).append(" đề tài** | Đang hướng dẫn: **").append(sup).append(" đề tài**");
            if (rev == 0) {
                sb.append("  👉 *(Đang rất rảnh, ưu tiên chọn)*\n");
            } else if (rev <= 2) {
                sb.append("  👉 *(Tải vừa phải, phù hợp)*\n");
            } else {
                sb.append("  👉 *(Tải cao)*\n");
            }
        }

        sb.append("\n🔒 **Quy tắc bất biến**: Hệ thống tự động khóa, **không cho phép phân công GVHD chính hoặc phụ** làm Giảng viên Phản biện cho chính đề tài đó.\n");
        sb.append("👉 Thầy/Cô có thể vào mục [Phân công Đề tài](/department-head/topics) để tiến hành gán GVPB trực tiếp.");

        return new AiResponse(sb.toString(), List.of("Đề tài chưa có GVPB", "Quy chế tính điểm", "Danh sách Hội đồng"));
    }

    /**
     * Quy chế & Công thức tính điểm chuẩn số học.
     */
    private AiResponse handleGradingFormulaQuery(String q) {
        if (q.contains("lệch") || q.contains("2.0") || q.contains("đối thoại")) {
            String reply = """
                    ⚖️ **QUY ĐỊNH XỬ LÝ ĐỘ LỆCH ĐIỂM HỘI ĐỒNG (> 2.0 ĐIỂM):**
                    
                    * **Quy tắc**: Khi mức điểm giữa 2 thành viên trong Hội đồng chấm lệch nhau **vượt quá 2.0 điểm**, hệ thống sẽ tự động bật cờ cảnh báo `hasDiscrepancy = true`.
                    * **Quy trình xử lý**:
                      1. Hội đồng tiến hành **đối thoại trực tiếp** để thống nhất và xem xét lại các tiêu chí đánh giá.
                      2. Thành viên có thể điều chỉnh lại điểm trên hệ thống.
                      3. Trường hợp giữ nguyên quan điểm, **Chủ tịch Hội đồng** sẽ ghi nhận ý kiến vào Biên bản bảo vệ và quyết định điểm số trung bình cuối cùng.
                    """;
            return new AiResponse(reply, List.of("Công thức tính điểm KLTN", "Quy tắc xung đột lợi ích", "Danh sách Hội đồng"));
        }

        String reply = """
                📊 **QUY CHẾ TÍNH ĐIỂM CHÍNH THỨC (Chuẩn Trung Bình Cộng Số Học):**
                
                Theo quy định của Khoa và Nhà trường, toàn bộ điểm số được tính theo **Trung bình cộng (Arithmetic Mean)** và làm tròn **2 chữ số thập phân (`0.01`)**:
                
                1. **Đợt Môn Học / NCKH (`MON_HOC`, `NCKH`)**:
                   * $\\text{Điểm Tổng Kết} = \\text{Điểm GVHD}$ *(hoặc TBC nếu có 2 GVHD)*.
                
                2. **Đợt Tiểu Luận Chuyên Ngành (`TLCN`)**:
                   * $\\text{Điểm Tổng Kết} = \\frac{\\text{Điểm GVHD} + \\text{Điểm GVPB}}{2}$.
                
                3. **Đợt Khóa Luận Tốt Nghiệp (`KLTN`)**:
                   * $\\text{Điểm Hội Đồng} = \\frac{1}{N} \\sum_{i=1}^{N} \\text{Điểm Thành Viên}_i$ *(3 đến 5 giảng viên)*.
                   * $\\text{Điểm Tổng Kết} = \\frac{\\text{Điểm GVHD} + \\text{Điểm GVPB} + \\text{Điểm Hội Đồng}}{3}$.
                
                🔒 **Quy tắc xung đột lợi ích**: Giảng viên không được chấm điểm đề tài do chính mình hướng dẫn khi ở vai trò Phản biện hoặc Hội đồng.
                """;
        return new AiResponse(reply, List.of("Điều kiện bảo vệ KLTN", "Giảng viên nào đang rảnh để phân GVPB", "Tiến độ nhóm của tôi"));
    }

    /**
     * Điều kiện bảo vệ & Điều kiện đạt đề tài.
     */
    private AiResponse handleDefenseEligibilityQuery() {
        String reply = """
                🎓 **ĐIỀU KIỆN ĐƯỢC BẢO VỆ & CÔNG NHẬN ĐẠT ĐỀ TÀI:**
                
                1. **Điều kiện được ra Hội đồng bảo vệ KLTN**:
                   * Điểm đánh giá của **Giảng viên hướng dẫn (GVHD) đạt $\\ge 5.0 / 10$**.
                   * Điểm đánh giá của **Giảng viên phản biện (GVPB) đạt $\\ge 5.0 / 10$**.
                   * Nếu một trong hai điểm $< 5.0$, đề tài không đủ điều kiện ra bảo vệ Hội đồng.
                
                2. **Điều kiện công nhận Đạt (Pass) đề tài**:
                   * **Điểm Tổng Kết cuối cùng** phải đạt **$\\ge 5.0 / 10$**.
                   * Hoàn thành đầy đủ các báo cáo tiến độ và nộp bản quyển Khóa luận hoàn chỉnh đúng hạn.
                """;
        return new AiResponse(reply, List.of("Quy chế tính điểm", "Hạn nộp báo cáo", "Tiến độ nhóm của tôi"));
    }

    /**
     * Quy định về Nhóm sinh viên & Quyền nộp báo cáo.
     */
    private AiResponse handleGroupRulesAndSubmissionPermissionsQuery(AppUser user) {
        String reply = """
                👥 **QUY ĐỊNH NHÓM SINH VIÊN & QUYỀN NỘP BÁO CÁO:**
                
                * **Quy mô nhóm**: Mỗi nhóm sinh viên có từ **1 đến 3 thành viên** (tùy theo cấu hình từng đợt).
                * 🔑 **Quyền nộp bài báo cáo**:
                  * Chỉ duy nhất **Trưởng nhóm (Group Leader)** mới có quyền tải lên file nộp báo cáo tiến độ (file `.pdf`, `.zip`, `.rar`, `.docx`).
                  * Thành viên thông thường chỉ có quyền xem tiến độ, nhận xét và điểm số của GVHD.
                * 🔄 **Chuyển nhượng trưởng nhóm / Mời thành viên**:
                  * Trưởng nhóm có thể chia sẻ **Mã nhóm (Group Code)** cho bạn bè để nhập mã tham gia.
                  * Trưởng nhóm có thể chuyển quyền Leader cho thành viên khác trong mục [Quản lý Nhóm](/student/group).
                """;
        return new AiResponse(reply, List.of("Tiến độ nhóm của tôi", "Tìm đề tài còn trống", "Hạn nộp báo cáo"));
    }

    /**
     * Tra cứu Khoa quản lý đào tạo.
     */
    private AiResponse handleDepartmentQuery(String q) {
        List<Department> departments = departmentRepository.findAll();

        StringBuilder sb = new StringBuilder();
        sb.append("🏛️ **DANH SÁCH KHOA QUẢN LÝ ĐÀO TẠO:**\n\n");

        for (Department d : departments) {
            sb.append("* **").append(d.getName()).append("** (`").append(d.getCode()).append("`)\n");
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                sb.append("  * ").append(d.getDescription()).append("\n");
            }
        }

        return new AiResponse(sb.toString(), List.of("Giảng viên nào đang rảnh để phân GVPB", "Đề tài còn trống", "Tìm đề tài"));
    }

    /**
     * Hướng dẫn sử dụng các tính năng mới của hệ thống.
     */
    private AiResponse handleFeatureHowToQuery(String q) {
        if (q.contains("dark mode") || q.contains("tối") || q.contains("sáng")) {
            String reply = """
                    🌙 **HƯỚNG DẪN BẬT / TẮT GIAO DIỆN TỐI (DARK MODE):**
                    
                    * **Cách thực hiện**: Nhấp vào biểu tượng **Mặt trăng / Mặt trời (🌓)** ở góc trên bên phải thanh Menu (Topbar).
                    * **Tính năng**: Giao diện tối giúp dịu mắt khi làm việc ban đêm. Hệ thống tự động ghi nhớ tùy chọn này vào trình duyệt của bạn cho các lần đăng nhập tiếp theo!
                    """;
            return new AiResponse(reply, List.of("Xuất PDF phiếu điểm", "Xem biểu đồ thống kê", "Tiến độ nhóm của tôi"));
        }

        if (q.contains("pdf") || q.contains("in")) {
            String reply = """
                    🖨️ **HƯỚNG DẪN IN & XUẤT PDF PHIẾU ĐÁNH GIÁ / BIÊN BẢN HỘI ĐỒNG:**
                    
                    1. Đăng nhập với tài khoản **Trưởng khoa / Trưởng bộ môn**.
                    2. Truy cập mục **[Kết quả đánh giá](/department-head/results)** từ menu bên trái.
                    3. Nhấn vào nút **\"In / Xuất PDF Phiếu Đánh Giá\"** cạnh đề tài tương ứng.
                    4. Trình duyệt sẽ mở cửa sổ in ấn A4 chuẩn mẫu Bộ Giáo dục: bạn có thể **In trực tiếp** ra máy in hoặc chọn **Save as PDF** để lưu về máy!
                    """;
            return new AiResponse(reply, List.of("Xem biểu đồ thống kê", "Quy chế tính điểm", "Thống kê toàn bộ môn"));
        }

        String reply = """
                📊 **HƯỚNG DẪN XEM BIỂU ĐỒ TRỰC QUAN (ANALYTICS CHART.JS):**
                
                * Truy cập trang **[Bảng điều khiển Trưởng khoa](/department-head/dashboard)**.
                * Bạn sẽ thấy 2 biểu đồ trực quan tương tác:
                  1. 🍩 **Biểu đồ tròn (Doughnut Chart)**: Phân bố phổ điểm sinh viên (Xuất sắc, Giỏi, Khá, Trung bình, Không đạt).
                  2. 📊 **Biểu đồ cột (Bar Chart)**: Pipeline tiến trình đề tài (Chờ duyệt, Đã duyệt, Đã gán GVPB, Đã gán Hội đồng, Đã công bố điểm).
                """;
        return new AiResponse(reply, List.of("In xuất PDF phiếu điểm", "Giảng viên nào đang rảnh để phân GVPB", "Thống kê toàn bộ môn"));
    }

    private AiResponse handleProgressAndSubmissionQuery(AppUser user, String roleCode, String q) {
        if (user == null) {
            return new AiResponse("Bạn cần [Đăng nhập](/login) vào hệ thống để tra cứu thông tin tiến độ nhóm hoặc đề tài của mình.",
                    getDefaultSuggestions(null));
        }

        if ("STUDENT".equalsIgnoreCase(roleCode)) {
            Student student = studentRepository.findByUserId(user.getId()).orElse(null);
            if (student == null) {
                return new AiResponse("Không tìm thấy hồ sơ sinh viên của bạn trong hệ thống.", getDefaultSuggestions(user));
            }

            List<StudentGroup> myGroups = groupRepository.findByStudentId(student.getId());
            StudentGroup group = myGroups.stream()
                    .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()) && !"REJECTED".equalsIgnoreCase(g.getStatus()))
                    .findFirst()
                    .orElse(myGroups.isEmpty() ? null : myGroups.get(0));

            if (group == null) {
                return new AiResponse("Hiện tại bạn **chưa tham gia nhóm sinh viên nào**. Hãy vào mục [Quản lý nhóm](/student/group) để tạo nhóm mới hoặc nhập mã tham gia nhóm của bạn bè!",
                        List.of("Tìm đề tài mở", "Quy chế tính điểm", "Hạn chót các đợt"));
            }

            Topic topic = group.getTopic();
            List<Submission> submissions = submissionRepository.findByGroupId(group.getId());

            StringBuilder sb = new StringBuilder();
            sb.append("📋 **THÔNG TIN TIẾN ĐỘ NHÓM CỦA BẠN:**\n\n");
            sb.append("* **Tên nhóm**: `").append(group.getGroupName()).append("` (Mã: `").append(group.getGroupCode()).append("`)\n");
            sb.append("* **Trưởng nhóm**: ").append(group.getLeader() != null && group.getLeader().getUser() != null ? group.getLeader().getUser().getFullName() : "Chưa rõ").append("\n");
            sb.append("* **Trạng thái nhóm**: `").append(group.getStatus()).append("`\n");

            if (topic != null) {
                sb.append("* **Đề tài**: **").append(topic.getTitle()).append("** (`").append(topic.getCode()).append("`)\n");
                sb.append("* **GVHD**: ").append(topic.getSupervisor() != null && topic.getSupervisor().getUser() != null ? topic.getSupervisor().getUser().getFullName() : "Chưa phân công").append("\n");
                if (topic.getFinalScore() != null && Boolean.TRUE.equals(topic.getIsFinalized())) {
                    sb.append("* **Điểm tổng kết đã công bố**: 🏆 **").append(topic.getFinalScore()).append(" / 10**\n");
                }
            } else {
                sb.append("* **Đề tài**: *Nhóm chưa đăng ký đề tài nào.* Hãy vào mục [Tìm đề tài](/student/topics) để đăng ký ngay!\n");
            }

            sb.append("\n📁 **Lịch sử bài nộp báo cáo (").append(submissions.size()).append(" bài):**\n");
            if (submissions.isEmpty()) {
                sb.append("*(Chưa có bài nộp nào. Lưu ý: Chỉ Nhóm trưởng mới có quyền tải lên file báo cáo)*\n");
            } else {
                for (Submission s : submissions) {
                    sb.append("- **Vòng ").append(s.getSubmissionRound()).append("**: *").append(s.getTitle()).append("*");
                    if (s.getGrade() != null) {
                        sb.append(" ➔ Điểm: **").append(s.getGrade()).append("/10** (Nhận xét: \"").append(s.getFeedback() != null ? s.getFeedback() : "Tốt").append("\")\n");
                    } else {
                        sb.append(" ➔ *(Chờ GVHD chấm)*\n");
                    }
                }
            }

            return new AiResponse(sb.toString(), List.of("Quy chế tính điểm", "Hạn nộp báo cáo tiếp theo", "Xem bảng điểm"));
        } else if ("LECTURER".equalsIgnoreCase(roleCode)) {
            Lecturer lecturer = lecturerRepository.findByUserId(user.getId()).orElse(null);
            if (lecturer == null) return new AiResponse("Không tìm thấy hồ sơ giảng viên.", getDefaultSuggestions(user));

            List<Topic> myTopics = topicRepository.findBySupervisorId(lecturer.getId());
            List<Submission> pendingSubmissions = submissionRepository.findAll().stream()
                    .filter(s -> s.getTopic() != null && s.getTopic().getSupervisor() != null && s.getTopic().getSupervisor().getId().equals(lecturer.getId()))
                    .filter(s -> s.getGrade() == null)
                    .toList();

            StringBuilder sb = new StringBuilder();
            sb.append("👨‍🏫 **TỔNG HỢP TIẾN ĐỘ HƯỚNG DẪN CỦA THẦY/CÔ:**\n\n");
            sb.append("* Thầy/Cô đang hướng dẫn: **").append(myTopics.size()).append(" đề tài**.\n");
            sb.append("* Số bài báo cáo tiến độ **chờ chấm điểm**: **").append(pendingSubmissions.size()).append(" bài**.\n\n");

            if (!pendingSubmissions.isEmpty()) {
                sb.append("📝 **Danh sách bài nộp cần phản hồi:**\n");
                for (Submission s : pendingSubmissions.stream().limit(5).toList()) {
                    sb.append("- Đề tài `").append(s.getTopic() != null ? s.getTopic().getCode() : "").append("`: *")
                            .append(s.getTitle()).append("* (Vòng ").append(s.getSubmissionRound()).append(") ➔ [Chấm ngay](/lecturer/submissions)\n");
                }
            } else {
                sb.append("✅ Tất cả các bài nộp hiện tại đã được chấm điểm đầy đủ!\n");
            }

            return new AiResponse(sb.toString(), List.of("Đề tài tôi hướng dẫn", "Hội đồng của tôi", "Quy chế tính điểm"));
        } else {
            // DEPARTMENT_HEAD / ADMIN
            List<StudentGroup> allGroups = groupRepository.findAll();
            long assignedGroups = allGroups.stream().filter(g -> g.getTopic() != null).count();
            List<Topic> allTopics = topicRepository.findAll();
            long pendingTopics = allTopics.stream().filter(t -> "PENDING".equalsIgnoreCase(t.getStatus())).count();

            StringBuilder sb = new StringBuilder();
            sb.append("📊 **TIẾN ĐỘ QUẢN LÝ NHÓM & ĐỀ TÀI (DÀNH CHO KHOA):**\n\n");
            sb.append("* Tổng số nhóm sinh viên: **").append(allGroups.size()).append(" nhóm** (trong đó **").append(assignedGroups).append(" nhóm** đã có đề tài).\n");
            sb.append("* Đề tài chờ duyệt: **").append(pendingTopics).append(" đề tài**.\n\n");
            sb.append("👉 Thầy/Cô có thể xem danh sách sinh viên tại **[Quản lý Nhóm Sinh viên](/department-head/students)** hoặc duyệt đề tài tại **[Quản lý Đề tài](/department-head/topics)**.");

            return new AiResponse(sb.toString(), List.of("Đề tài chờ duyệt", "Giảng viên nào đang rảnh để phân GVPB", "Thống kê toàn bộ môn"));
        }
    }

    private AiResponse handleTopicQuery(AppUser user, String roleCode, String q) {
        List<Topic> approvedTopics = topicRepository.findAll().stream()
                .filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus()))
                .toList();

        List<StudentGroup> allGroups = groupRepository.findAll();
        Map<Long, StudentGroup> topicGroupMap = new HashMap<>();
        for (StudentGroup g : allGroups) {
            if (g.getTopic() != null) topicGroupMap.put(g.getTopic().getId(), g);
        }

        List<Topic> availableTopics = approvedTopics.stream()
                .filter(t -> !topicGroupMap.containsKey(t.getId()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("💡 **THÔNG TIN ĐỀ TÀI TRONG HỆ THỐNG:**\n\n");
        sb.append("* Tổng số đề tài đã duyệt: **").append(approvedTopics.size()).append("** đề tài.\n");
        sb.append("* Số đề tài **còn trống (chưa có nhóm đăng ký)**: **").append(availableTopics.size()).append("** đề tài.\n\n");

        if (!availableTopics.isEmpty()) {
            sb.append("🌟 **Một số đề tài mở nổi bật:**\n");
            for (Topic t : availableTopics.stream().limit(4).toList()) {
                sb.append("- [`").append(t.getCode()).append("`] **").append(t.getTitle()).append("**\n");
                sb.append("  * *GVHD*: ").append(t.getSupervisor() != null && t.getSupervisor().getUser() != null ? t.getSupervisor().getUser().getFullName() : "Chưa rõ");
                sb.append(" | *Khoa*: ").append(t.getDepartment() != null ? t.getDepartment().getName() : "Khoa CNTT").append("\n");
            }
            sb.append("\n👉 Truy cập [Danh sách Đề tài](/student/topics) để đăng ký ngay!");
        } else {
            sb.append("Hiện tại các đề tài đã được đăng ký đầy đủ.");
        }

        return new AiResponse(sb.toString(), List.of("Tiến độ nhóm của tôi", "Hạn chót các đợt", "Quy chế tính điểm"));
    }

    private AiResponse handlePeriodAndDeadlineQuery() {
        List<RegistrationPeriod> periods = periodRepository.findAll();
        if (periods.isEmpty()) {
            return new AiResponse("Hiện tại chưa có đợt đăng ký nào được mở trên hệ thống.", List.of("Quy chế tính điểm", "Tìm đề tài"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📅 **LỊCH TRÌNH & CÁC MỐC THỜI HẠN QUAN TRỌNG:**\n\n");

        for (RegistrationPeriod p : periods) {
            sb.append("### 📌 ").append(p.getName()).append(" (`").append(p.getPeriodType()).append("`)\n");
            if (p.getLecturerProposalStart() != null && p.getLecturerProposalEnd() != null) {
                sb.append("* **Đề xuất đề tài**: ").append(p.getLecturerProposalStart().format(DATE_FMT)).append(" ➔ ").append(p.getLecturerProposalEnd().format(DATE_FMT)).append("\n");
            }
            if (p.getStudentRegistrationStart() != null && p.getStudentRegistrationEnd() != null) {
                sb.append("* **Sinh viên đăng ký**: ").append(p.getStudentRegistrationStart().format(DATE_FMT)).append(" ➔ ").append(p.getStudentRegistrationEnd().format(DATE_FMT)).append("\n");
            }
            if (p.getReviewerDeadline() != null) {
                sb.append("* **Hạn chót Phản biện**: **").append(p.getReviewerDeadline().format(DATE_FMT)).append("**\n");
            }
            if (p.getCouncilDefenseDate() != null) {
                sb.append("* **Ngày bảo vệ Hội đồng**: 🎓 **").append(p.getCouncilDefenseDate().format(DATE_FMT)).append("**\n");
            }
            sb.append("\n");
        }

        return new AiResponse(sb.toString(), List.of("Quy chế tính điểm", "Tiến độ nhóm của tôi", "Tìm đề tài mở"));
    }

    private AiResponse handleCouncilQuery(AppUser user, String roleCode) {
        List<Council> councils = councilRepository.findAll().stream()
                .filter(c -> !c.getName().contains("CONFLICT") && !c.getName().matches(".*_\\d{10,}.*"))
                .toList();

        if (councils.isEmpty()) {
            return new AiResponse("Hiện tại các Hội đồng bảo vệ chính thức đang trong quá trình thành lập.", List.of("Lịch trình đợt", "Quy chế tính điểm"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🏛️ **DANH SÁCH HỘI ĐỒNG BẢO VỆ CHÍNH THỨC:**\n\n");

        for (Council c : councils.stream().limit(5).toList()) {
            sb.append("* **").append(c.getName()).append("** (`").append(c.getCode() != null ? c.getCode() : "HD-" + c.getId()).append("`)\n");
            sb.append("  * *Địa điểm*: ").append(c.getLocation() != null ? c.getLocation() : "Phòng bảo vệ Khoa");
            if (c.getDefenseDate() != null) {
                sb.append(" | *Thời gian*: ").append(c.getDefenseDate().format(DATE_FMT));
            }
            sb.append("\n  * *Thành viên*: ");
            if (c.getMembers() != null && !c.getMembers().isEmpty()) {
                String membersStr = c.getMembers().stream()
                        .map(m -> (m.getLecturer() != null && m.getLecturer().getUser() != null ? m.getLecturer().getUser().getFullName() : "GV") + " (" + m.getRoleName() + ")")
                        .collect(Collectors.joining(", "));
                sb.append(membersStr);
            } else {
                sb.append("Chưa chỉ định");
            }
            sb.append("\n\n");
        }

        return new AiResponse(sb.toString(), List.of("Quy chế tính điểm", "Giảng viên nào đang rảnh để phân GVPB", "Hạn nộp báo cáo"));
    }

    private AiResponse handleStatisticsQuery(AppUser user, String roleCode) {
        List<Topic> allTopics = topicRepository.findAll();
        long pending = allTopics.stream().filter(t -> "PENDING".equalsIgnoreCase(t.getStatus())).count();
        long approved = allTopics.stream().filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus())).count();
        long finalized = allTopics.stream().filter(t -> Boolean.TRUE.equals(t.getIsFinalized())).count();

        List<StudentGroup> allGroups = groupRepository.findAll();
        List<Council> councils = councilRepository.findAll();

        String reply = String.format("""
                📈 **TỔNG HỢP SỐ LIỆU TOÀN HỆ THỐNG:**
                
                * **Tổng số đề tài**: **%d** đề tài.
                  * ⏳ *Chờ phê duyệt*: **%d** đề tài.
                  * ✅ *Đã phê duyệt*: **%d** đề tài.
                  * 🏆 *Đã công bố điểm chính thức*: **%d** đề tài.
                * **Số nhóm sinh viên hoạt động**: **%d** nhóm.
                * **Số Hội đồng bảo vệ đã thành lập**: **%d** hội đồng.
                
                Thầy/Cô có thể vào [Bảng điều khiển](/department-head/dashboard) hoặc [Quản lý Đề tài](/department-head/topics) để xử lý chi tiết!
                """, allTopics.size(), pending, approved, finalized, allGroups.size(), councils.size());

        return new AiResponse(reply, List.of("Giảng viên nào đang rảnh để phân GVPB", "Đề tài chờ duyệt", "Quy chế tính điểm"));
    }

    private AiResponse handleGreetingQuery(AppUser user) {
        String name = user != null ? user.getFullName() : "Bạn";
        String reply = String.format("""
                👋 Xin chào **%s**! Tôi là **TopicHub AI Assistant** – Trợ lý thông minh hỗ trợ quản lý và tra cứu đề tài sinh viên.
                
                Bạn có thể hỏi tôi bất kỳ điều gì:
                * 👨‍🏫 *Giảng viên nào đang rảnh để phân công GVPB?*
                * 🔍 *Tra cứu thông tin / tải của giảng viên X?*
                * 📊 *Tiến độ nhóm sinh viên và lịch sử nộp báo cáo?*
                * 📝 *Quy chế và công thức tính điểm KLTN / TLCN / Môn học?*
                * 💡 *Tra cứu các đề tài mở còn chỗ trống?*
                * 📅 *Hạn nộp báo cáo và lịch bảo vệ Hội đồng sắp tới?*
                * 🖨️ *Cách in xuất PDF phiếu điểm hay bật giao diện Dark mode?*
                """, name);

        return new AiResponse(reply, getDefaultSuggestions(user));
    }

    private AiResponse handleSmartSearchFallback(String query, AppUser user) {
        String keyword = query.trim();
        List<Topic> matchedTopics = topicRepository.findAll().stream()
                .filter(t -> (t.getTitle() != null && t.getTitle().toLowerCase().contains(keyword.toLowerCase()))
                        || (t.getCode() != null && t.getCode().toLowerCase().contains(keyword.toLowerCase())))
                .limit(4)
                .toList();

        if (!matchedTopics.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("🔍 Tôi tìm thấy một số đề tài liên quan đến từ khóa **\"").append(keyword).append("\"**:\n\n");
            for (Topic t : matchedTopics) {
                sb.append("* [`").append(t.getCode()).append("`] **").append(t.getTitle()).append("**\n");
                sb.append("  * Trạng thái: `").append(t.getStatus()).append("` | GVHD: ")
                        .append(t.getSupervisor() != null && t.getSupervisor().getUser() != null ? t.getSupervisor().getUser().getFullName() : "Chưa rõ").append("\n");
            }
            return new AiResponse(sb.toString(), List.of("Quy chế tính điểm", "Tiến độ nhóm của tôi", "Hạn nộp báo cáo"));
        }

        return new AiResponse("Tôi chưa hiểu rõ câu hỏi này. Bạn có thể chọn một trong các gợi ý nhanh bên dưới hoặc hỏi về **tiến độ nhóm**, **công thức tính điểm**, **tìm đề tài**, **giảng viên rảnh để phân GVPB**, hoặc **hướng dẫn in PDF / Dark mode**!",
                getDefaultSuggestions(user));
    }

    private List<String> getDefaultSuggestions(AppUser user) {
        String role = user != null && user.getRole() != null ? user.getRole().getCode() : "STUDENT";
        if ("STUDENT".equalsIgnoreCase(role)) {
            return List.of("Tiến độ nhóm của tôi", "Công thức tính điểm KLTN", "Tìm đề tài còn trống", "Hạn chót các đợt");
        } else if ("LECTURER".equalsIgnoreCase(role)) {
            return List.of("Tiến độ các nhóm tôi hướng dẫn", "Hội đồng của tôi", "Quy chế tính điểm", "Đề tài chờ duyệt");
        } else {
            return List.of("Giảng viên nào đang rảnh để phân GVPB", "Đề tài đang chờ duyệt", "Thống kê toàn bộ môn", "Quy chế tính điểm");
        }
    }
}
