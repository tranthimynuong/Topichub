package topicmanagement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import topicmanagement.entity.AppUser;
import topicmanagement.entity.GroupMember;
import topicmanagement.entity.Student;
import topicmanagement.entity.StudentGroup;
import topicmanagement.entity.Topic;
import topicmanagement.repository.StudentGroupRepository;
import topicmanagement.repository.TopicRepository;

import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Dịch vụ gửi email thông báo kết quả công bố điểm, chào mừng tài khoản,
 * phê duyệt đề tài và mời tham gia nhóm theo Rubric môn học.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TopicRepository topicRepository;
    private final StudentGroupRepository groupRepository;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            TopicRepository topicRepository,
            StudentGroupRepository groupRepository) {
        this.mailSender = mailSender;
        this.topicRepository = topicRepository;
        this.groupRepository = groupRepository;
    }

    /**
     * 1) Gửi email chào mừng thành viên mới và cung cấp thông tin đăng nhập ban đầu.
     */
    @Async
    public void sendWelcomeEmail(AppUser user, String rawPassword) {
        if (user == null) return;
        String toEmail = user.getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("[EMAIL-WELCOME] Người dùng {} ({}) chưa có email để nhận thông tin đăng nhập.",
                    user.getFullName(), user.getUsername());
            return;
        }

        String username = user.getUsername() != null ? user.getUsername() : "";
        String fullName = (user.getFullName() != null && !user.getFullName().isBlank()) ? user.getFullName() : username;
        String roleName = user.getRole() != null ? (user.getRole().getName() != null ? user.getRole().getName() : user.getRole().getCode()) : "Thành viên";
        String initialPassword = (rawPassword != null && !rawPassword.isBlank()) ? rawPassword : "Chưa đặt (vui lòng liên hệ Admin)";

        String subject = "[TopicHub] Chào mừng tài khoản mới: " + username;

        String htmlContent = """
            <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff;">
                <div style="text-align: center; margin-bottom: 20px;">
                    <h2 style="color: #2563eb; margin: 0;">TopicHub - Chào Mừng Thành Viên Mới</h2>
                    <p style="color: #64748b; font-size: 14px; margin: 5px 0 0 0;">Hệ Thống Quản Lý Đề Tài Tốt Nghiệp</p>
                </div>
                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 15px 0;" />
                <p>Xin chào <strong>%s</strong>,</p>
                <p>Tài khoản của bạn đã được Quản trị viên khởi tạo thành công trên hệ thống <strong>TopicHub</strong>. Dưới đây là thông tin đăng nhập ban đầu của bạn:</p>
                <table style="width: 100%%; border-collapse: collapse; margin: 15px 0; font-size: 14px;">
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold; width: 35%%;">Tên đăng nhập:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; font-family: monospace; font-size: 15px; font-weight: bold; color: #1e40af;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Mật khẩu ban đầu:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; font-family: monospace; font-size: 15px; font-weight: bold; color: #0f172a;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Email đăng ký:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Vai trò:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0;">%s</td>
                    </tr>
                </table>
                <div style="background: #eff6ff; border-left: 4px solid #2563eb; padding: 12px 16px; margin: 15px 0; border-radius: 4px; font-size: 13px; color: #1e40af;">
                    <strong>Lưu ý bảo mật:</strong> Vui lòng đăng nhập vào hệ thống và đổi lại mật khẩu cá nhân ngay sau lần đăng nhập đầu tiên.
                </div>
                <div style="margin-top: 25px; padding-top: 15px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #94a3b8; text-align: center;">
                    Email này được gửi tự động từ Ban Quản trị Hệ thống TopicHub. Vui lòng không trả lời trực tiếp email này.
                </div>
            </div>
            """.formatted(fullName, username, initialPassword, toEmail, roleName);

        if (mailSender == null || senderEmail == null || senderEmail.isBlank()) {
            log.info("[EMAIL-SIMULATION] Cấu hình SMTP chưa kích hoạt. Giả lập gửi welcome email tới {} ({}) với username: {}",
                    toEmail, fullName, username);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL-SUCCESS] Đã gửi welcome email tới người dùng {} <{}>", username, toEmail);
        } catch (Exception e) {
            log.error("[EMAIL-ERROR] Gửi welcome email tới {} thất bại: {}", toEmail, e.getMessage());
        }
    }

    /**
     * 2) Gửi email thông báo kết quả thẩm định đề tài (duyệt / từ chối) cho Giảng viên hướng dẫn.
     * Gửi cho GVHD qua topic.getSupervisor().getUser().getEmail().
     */
    @Async
    public void sendTopicApprovalEmail(Topic topic, boolean approved, String reason) {
        if (topic == null || topic.getSupervisor() == null || topic.getSupervisor().getUser() == null) {
            log.warn("[EMAIL-TOPIC-APPROVAL] Không tìm thấy thông tin GVHD cho đề tài để gửi mail thẩm định.");
            return;
        }

        AppUser supervisorUser = topic.getSupervisor().getUser();
        String toEmail = supervisorUser.getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("[EMAIL-TOPIC-APPROVAL] GVHD {} ({}) chưa có email để nhận thông báo thẩm định đề tài.",
                    supervisorUser.getFullName(), supervisorUser.getUsername());
            return;
        }

        String supervisorName = (supervisorUser.getFullName() != null && !supervisorUser.getFullName().isBlank())
                ? supervisorUser.getFullName()
                : supervisorUser.getUsername();
        String topicTitle = topic.getTitle() != null ? topic.getTitle() : "Đề tài tốt nghiệp";
        String topicCode = topic.getCode() != null ? topic.getCode() : "N/A";

        String statusTitle = approved ? "Phê Duyệt Đề Tài" : "Từ Chối / Yêu Cầu Chỉnh Sửa Đề Tài";
        String statusText = approved ? "ĐÃ ĐƯỢC PHÊ DUYỆT" : "TỪ CHỐI / CHƯA ĐẠT YÊU CẦU";
        String statusColor = approved ? "#16a34a" : "#dc2626";
        String statusBg = approved ? "#f0fdf4" : "#fef2f2";
        String subject = "[TopicHub] " + (approved ? "Đề tài đã được phê duyệt: " : "Thông báo kết quả duyệt đề tài: ") + topicTitle;

        String reasonSection = (!approved && reason != null && !reason.isBlank())
                ? """
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #fef2f2; font-weight: bold; color: #dc2626;">Lý do / Ghi chú:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #fef2f2; color: #991b1b;">%s</td>
                    </tr>
                  """.formatted(reason)
                : "";

        String noteText = approved
                ? "Đề tài đã sẵn sàng để sinh viên đăng ký trong đợt đăng ký hiện hành. Quý Thầy/Cô có thể theo dõi danh sách đăng ký tại cổng thông tin Giảng viên."
                : "Quý Thầy/Cô vui lòng kiểm tra lý do và chỉnh sửa lại thông tin đề tài trước khi gửi duyệt lại.";

        String htmlContent = """
            <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff;">
                <div style="text-align: center; margin-bottom: 20px;">
                    <h2 style="color: %s; margin: 0;">TopicHub - %s</h2>
                    <p style="color: #64748b; font-size: 14px; margin: 5px 0 0 0;">Ban Chủ Nhiệm Khoa</p>
                </div>
                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 15px 0;" />
                <p>Kính gửi Quý Thầy/Cô <strong>%s</strong>,</p>
                <p>Ban Chủ nhiệm Khoa đã hoàn tất xem xét và thẩm định đề tài do Thầy/Cô đề xuất:</p>
                <table style="width: 100%%; border-collapse: collapse; margin: 15px 0; font-size: 14px;">
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold; width: 35%%;">Tên đề tài:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0;">%s (%s)</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Kết quả thẩm định:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: %s; color: %s; font-weight: bold;">%s</td>
                    </tr>
                    %s
                </table>
                <div style="background: #f8fafc; border-left: 4px solid %s; padding: 12px 16px; margin: 15px 0; border-radius: 4px; font-size: 13px; color: #334155;">
                    %s
                </div>
                <div style="margin-top: 25px; padding-top: 15px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #94a3b8; text-align: center;">
                    Email này được gửi tự động từ Ban Chủ Nhiệm Khoa trên Hệ thống TopicHub. Vui lòng không trả lời trực tiếp email này.
                </div>
            </div>
            """.formatted(statusColor, statusTitle, supervisorName, topicTitle, topicCode, statusBg, statusColor, statusText, reasonSection, statusColor, noteText);

        if (mailSender == null || senderEmail == null || senderEmail.isBlank()) {
            log.info("[EMAIL-SIMULATION] Cấu hình SMTP chưa kích hoạt. Giả lập gửi mail thẩm định đề tài '{}' (approved={}) tới GVHD {} ({})",
                    topicTitle, approved, toEmail, supervisorName);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL-SUCCESS] Đã gửi mail thẩm định đề tài '{}' tới GVHD {} <{}>",
                    topicTitle, supervisorName, toEmail);
        } catch (Exception e) {
            log.error("[EMAIL-ERROR] Gửi email thẩm định đề tài tới {} thất bại: {}", toEmail, e.getMessage());
        }
    }


    /**
     * Gửi email thông báo lời mời tham gia nhóm đề tài với đầy đủ thông tin đối tượng.
     */
    @Async
    public void sendGroupInvitationEmail(Student invitee, StudentGroup group, Student leader) {
        if (invitee == null || invitee.getUser() == null) {
            log.warn("[EMAIL-INVITATION] Không tìm thấy thông tin sinh viên được mời để gửi email.");
            return;
        }

        AppUser user = invitee.getUser();
        String toEmail = user.getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("[EMAIL-INVITATION] Sinh viên được mời {} ({}) chưa có email để nhận lời mời nhóm.",
                    user.getFullName(), invitee.getStudentCode());
            return;
        }

        String inviteeName = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : (invitee.getStudentCode() != null ? invitee.getStudentCode() : user.getUsername());
        String leaderName = (leader != null && leader.getUser() != null && leader.getUser().getFullName() != null && !leader.getUser().getFullName().isBlank())
                ? leader.getUser().getFullName()
                : (leader != null && leader.getStudentCode() != null ? leader.getStudentCode() : "Trưởng nhóm");
        String groupName = (group != null && group.getGroupName() != null && !group.getGroupName().isBlank())
                ? group.getGroupName()
                : (group != null ? group.getGroupCode() : "Nhóm đề tài");
        String groupCode = (group != null && group.getGroupCode() != null) ? group.getGroupCode() : "N/A";

        String subject = "[TopicHub] Lời mời tham gia nhóm: " + groupName;

        String htmlContent = """
            <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff;">
                <div style="text-align: center; margin-bottom: 20px;">
                    <h2 style="color: #2563eb; margin: 0;">TopicHub - Lời Mời Tham Gia Nhóm</h2>
                    <p style="color: #64748b; font-size: 14px; margin: 5px 0 0 0;">Cổng thông tin Sinh viên</p>
                </div>
                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 15px 0;" />
                <p>Xin chào <strong>%s</strong> (MSSV: <strong>%s</strong>),</p>
                <p>Bạn vừa nhận được một lời mời tham gia nhóm sinh viên thực hiện đề tài tốt nghiệp:</p>
                <table style="width: 100%%; border-collapse: collapse; margin: 15px 0; font-size: 14px;">
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold; width: 35%%;">Tên nhóm:</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; font-weight: bold; color: #1e40af;">%s (%s)</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Người mời (Trưởng nhóm):</td>
                        <td style="padding: 10px; border: 1px solid #e2e8f0;">%s</td>
                    </tr>
                </table>
                <div style="background: #eff6ff; border-left: 4px solid #2563eb; padding: 12px 16px; margin: 15px 0; border-radius: 4px; font-size: 13px; color: #1e40af;">
                    <strong>Hướng dẫn:</strong> Vui lòng đăng nhập vào hệ thống tại mục <strong>"Nhóm của tôi / Lời mời tham gia"</strong> để xem chi tiết và phản hồi (Đồng ý hoặc Từ chối) lời mời này.
                </div>
                <div style="margin-top: 25px; padding-top: 15px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #94a3b8; text-align: center;">
                    Email này được gửi tự động từ Hệ thống TopicHub. Vui lòng không trả lời trực tiếp email này.
                </div>
            </div>
            """.formatted(inviteeName, invitee.getStudentCode(), groupName, groupCode, leaderName);

        if (mailSender == null || senderEmail == null || senderEmail.isBlank()) {
            log.info("[EMAIL-SIMULATION] Cấu hình SMTP chưa kích hoạt. Giả lập gửi lời mời nhóm '{}' tới sinh viên {} ({})",
                    groupName, toEmail, inviteeName);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL-SUCCESS] Đã gửi email lời mời nhóm '{}' tới sinh viên {} <{}>",
                    groupName, inviteeName, toEmail);
        } catch (Exception e) {
            log.error("[EMAIL-ERROR] Gửi email lời mời nhóm tới {} thất bại: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Gửi email thông báo kết quả điểm số cho tất cả sinh viên trong nhóm thực hiện đề tài.
     */
    @Async
    @Transactional
    public void sendTopicResultEmailsToGroup(Long topicId, Long groupId) {
        if (topicId == null || groupId == null) return;

        Topic topic = topicRepository.findById(topicId).orElse(null);
        StudentGroup group = groupRepository.findById(groupId).orElse(null);
        if (topic == null || group == null) return;

        List<Student> students = new ArrayList<>();
        if (group.getLeader() != null) {
            students.add(group.getLeader());
        }
        if (group.getMembers() != null) {
            for (GroupMember gm : group.getMembers()) {
                if (gm.getStudent() != null && !students.contains(gm.getStudent())) {
                    students.add(gm.getStudent());
                }
            }
        }

        for (Student s : students) {
            sendResultEmailToStudent(topic, group, s);
        }
    }

    private void sendResultEmailToStudent(Topic topic, StudentGroup group, Student student) {
        if (student == null || student.getUser() == null) return;
        AppUser user = student.getUser();
        String toEmail = user.getEmail();
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("[EMAIL-RESULT] Sinh viên {} ({}) chưa có email để nhận thông báo kết quả.",
                    user.getFullName(), student.getStudentCode());
            return;
        }

        String subject = "[TopicHub] Công bố kết quả đánh giá đề tài: " + topic.getTitle();
        String finalScoreStr = topic.getFinalScore() != null ? String.format("%.2f", topic.getFinalScore()) : "Chưa có";
        String advisorScoreStr = topic.getAdvisorScore() != null ? String.format("%.2f", topic.getAdvisorScore()) : "Chưa có";
        String reviewerScoreStr = topic.getReviewerScore() != null ? String.format("%.2f", topic.getReviewerScore()) : "Chưa có";
        String councilScoreStr = topic.getCouncilScore() != null ? String.format("%.2f", topic.getCouncilScore()) : "Chưa có";

        String htmlContent = """
            <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px;">
                <div style="text-align: center; margin-bottom: 20px;">
                    <h2 style="color: #0f172a; margin: 0;">TopicHub - Thông Báo Kết Quả Đề Tài</h2>
                    <p style="color: #64748b; font-size: 14px; margin: 5px 0 0 0;">Khoa Công nghệ Thông tin</p>
                </div>
                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 15px 0;" />
                <p>Kính gửi sinh viên <strong>%s</strong> (MSSV: <strong>%s</strong>),</p>
                <p>Khoa xin thông báo kết quả đánh giá chính thức cho đề tài của nhóm bạn:</p>
                <table style="width: 100%%; border-collapse: collapse; margin: 15px 0; font-size: 14px;">
                    <tr>
                        <td style="padding: 8px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold; width: 35%%;">Tên đề tài:</td>
                        <td style="padding: 8px; border: 1px solid #e2e8f0;">%s (%s)</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Tên nhóm:</td>
                        <td style="padding: 8px; border: 1px solid #e2e8f0;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Điểm GV Hướng dẫn:</td>
                        <td style="padding: 8px; border: 1px solid #e2e8f0;">%s / 10</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Điểm GV Phản biện:</td>
                        <td style="padding: 8px; border: 1px solid #e2e8f0;">%s / 10</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #e2e8f0; background: #f8fafc; font-weight: bold;">Điểm Hội đồng:</td>
                        <td style="padding: 8px; border: 1px solid #e2e8f0;">%s / 10</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #e2e8f0; background: #e0f2fe; color: #0284c7; font-weight: bold; font-size: 16px;">Điểm Tổng Kết:</td>
                        <td style="padding: 8px; border: 1px solid #e2e8f0; background: #e0f2fe; color: #0284c7; font-weight: bold; font-size: 18px;">%s / 10</td>
                    </tr>
                </table>
                <p>Bạn có thể đăng nhập vào hệ thống để tra cứu chi tiết nhận xét của các thầy cô tại trang Tra cứu kết quả.</p>
                <div style="margin-top: 25px; padding-top: 15px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #94a3b8; text-align: center;">
                    Email này được gửi tự động từ Hệ thống Quản lý Đề tài Sinh viên TopicHub.
                </div>
            </div>
            """.formatted(
                user.getFullName(),
                student.getStudentCode() != null ? student.getStudentCode() : user.getUsername(),
                topic.getTitle(),
                topic.getCode() != null ? topic.getCode() : "N/A",
                group.getGroupName(),
                advisorScoreStr,
                reviewerScoreStr,
                councilScoreStr,
                finalScoreStr
        );

        if (mailSender == null || senderEmail == null || senderEmail.isBlank()) {
            log.info("[EMAIL-SIMULATION] Cấu hình SMTP chưa kích hoạt. Giả lập gửi thành công kết quả đề tài '{}' tới {} ({})",
                    topic.getTitle(), toEmail, user.getFullName());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EMAIL-SUCCESS] Đã gửi thông báo kết quả đề tài '{}' tới sinh viên {} <{}>",
                    topic.getTitle(), user.getFullName(), toEmail);
        } catch (Exception e) {
            log.error("[EMAIL-ERROR] Gửi email kết quả tới {} thất bại: {}", toEmail, e.getMessage());
        }
    }
}
