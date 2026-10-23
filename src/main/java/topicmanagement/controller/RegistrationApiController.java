package topicmanagement.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import topicmanagement.dto.RegistrationDtos.*;
import topicmanagement.entity.AppUser;
import topicmanagement.repository.UserRepository;
import topicmanagement.service.GroupRegistrationService;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/registrations", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@PreAuthorize("isAuthenticated()")
public class RegistrationApiController {

    private final GroupRegistrationService groupRegistrationService;
    private final UserRepository userRepository;

    public RegistrationApiController(GroupRegistrationService groupRegistrationService, UserRepository userRepository) {
        this.groupRegistrationService = groupRegistrationService;
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
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện thao tác!");
        }
        return user;
    }

    // ==================== 1. LUỒNG BẮT TAY 2 CHIỀU (INVITATIONS) ====================

    /**
     * Nhóm trưởng gửi lời mời tham gia nhóm tới sinh viên bằng studentCode.
     */
    @PostMapping("/groups/{groupId}/invite")
    public ResponseEntity<ApiResponse<InvitationResponseDto>> inviteMember(
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody InviteMemberRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        InvitationResponseDto dto = groupRegistrationService.sendInvitation(user, groupId, request.getStudentCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã gửi lời mời tham gia nhóm tới sinh viên thành công!", dto));
    }

    /**
     * Lấy danh sách tất cả các lời mời đang chờ phản hồi (status = INVITED) gửi tới sinh viên hiện tại.
     */
    @GetMapping("/invitations/my-invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponseDto>>> getMyInvitations(HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        List<InvitationResponseDto> list = groupRegistrationService.getMyPendingInvitations(user);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách lời mời thành công!", list));
    }

    /**
     * Lấy danh sách lời mời của một nhóm cụ thể.
     */
    @GetMapping("/groups/{groupId}/invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponseDto>>> getGroupInvitations(
            @PathVariable("groupId") Long groupId,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        List<InvitationResponseDto> list = groupRegistrationService.getGroupInvitations(user, groupId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách lời mời của nhóm thành công!", list));
    }

    /**
     * Sinh viên chấp nhận lời mời:
     * Chuyển trạng thái thành MEMBER chính thức, khóa quyền tham gia các nhóm khác,
     * tự động xóa các lời mời INVITED khác gửi tới sinh viên này.
     */
    @PostMapping("/invitations/{id}/accept")
    public ResponseEntity<ApiResponse<InvitationResponseDto>> acceptInvitation(
            @PathVariable("id") Long id,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        InvitationResponseDto dto = groupRegistrationService.acceptInvitation(user, id);
        return ResponseEntity.ok(ApiResponse.ok("Bạn đã gia nhập nhóm thành công! Các lời mời khác đã được tự động dọn dẹp.", dto));
    }

    /**
     * Sinh viên từ chối lời mời:
     * Xóa bản ghi lời mời để sinh viên tự do nhận lời mời khác hoặc tự tạo nhóm.
     */
    @PostMapping("/invitations/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectInvitation(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ResolveRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        String reason = (request != null) ? request.getRejectionReason() : null;
        ApiResponse<Void> response = groupRegistrationService.rejectInvitation(user, id, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * Nhóm trưởng hủy lời mời đã gửi trước đó.
     */
    @DeleteMapping("/invitations/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelInvitation(
            @PathVariable("id") Long id,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<Void> response = groupRegistrationService.cancelInvitation(user, id);
        return ResponseEntity.ok(response);
    }

    // ==================== 2. GIAI ĐOẠN DRAFT (OUT NHÓM & HỦY NHÓM TỰ DO) ====================

    /**
     * Giai đoạn DRAFT: Thành viên tự rời nhóm hoặc Nhóm trưởng xóa thành viên khỏi nhóm.
     */
    @DeleteMapping("/groups/{groupId}/members/{studentId}")
    public ResponseEntity<ApiResponse<Void>> removeMemberFromGroupDraft(
            @PathVariable("groupId") Long groupId,
            @PathVariable("studentId") Long studentId,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<Void> response = groupRegistrationService.leaveGroupDraft(user, groupId, studentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Giai đoạn DRAFT: Thành viên tự do rời nhóm theo endpoint rút gọn.
     */
    @DeleteMapping("/members/{studentId}")
    public ResponseEntity<ApiResponse<Void>> removeMemberShortcutDraft(
            @PathVariable("studentId") Long studentId,
            @RequestParam(value = "groupId", required = false) Long groupId,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        // Nếu groupId không truyền, tìm nhóm của sinh viên
        if (groupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng cung cấp groupId!");
        }
        ApiResponse<Void> response = groupRegistrationService.leaveGroupDraft(user, groupId, studentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Giai đoạn DRAFT: Nhượng quyền Trưởng nhóm (Transfer Leadership).
     */
    @PostMapping("/groups/{groupId}/transfer-leadership")
    public ResponseEntity<ApiResponse<Void>> transferLeadership(
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody TransferLeadershipRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<Void> response = groupRegistrationService.transferLeadership(user, groupId, request.getNewLeaderStudentId());
        return ResponseEntity.ok(response);
    }

    /**
     * Giai đoạn DRAFT: Nhóm trưởng bấm nút Hủy nhóm (Dissolve Group - xóa nhóm, giải phóng mọi người).
     */
    @PostMapping("/groups/{groupId}/dissolve")
    public ResponseEntity<ApiResponse<Void>> dissolveGroupDraftPost(
            @PathVariable("groupId") Long groupId,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<Void> response = groupRegistrationService.dissolveGroupDraft(user, groupId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/groups/{groupId}/dissolve")
    public ResponseEntity<ApiResponse<Void>> dissolveGroupDraftDelete(
            @PathVariable("groupId") Long groupId,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<Void> response = groupRegistrationService.dissolveGroupDraft(user, groupId);
        return ResponseEntity.ok(response);
    }

    // ==================== 3. GIAI ĐOẠN ĐANG THỰC HIỆN: EC-02 RÚT NHÓM ====================

    /**
     * Thành viên thường gửi yêu cầu xin rút nhóm lên GVHD duyệt.
     */
    @PostMapping("/groups/{groupId}/leave-requests")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> createLeaveRequest(
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody LeaveRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        LeaveRequestDto dto = groupRegistrationService.requestLeaveApprovedGroup(user, groupId, request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã gửi yêu cầu xin rút khỏi nhóm tới Giảng viên hướng dẫn thành công!", dto));
    }

    /**
     * GVHD xem danh sách các yêu cầu rút nhóm đang chờ duyệt (PENDING).
     */
    @GetMapping("/leave-requests/pending")
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getPendingLeaveRequests(HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        List<LeaveRequestDto> list = groupRegistrationService.getPendingLeaveRequestsForLecturer(user);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách yêu cầu rút nhóm thành công!", list));
    }

    /**
     * GVHD duyệt yêu cầu rút nhóm.
     * KÍCH HOẠT EC-02: Nếu sĩ số còn lại < MinStudents của Đợt, chuyển trạng thái nhóm thành PENDING_MEMBER và bắn cảnh báo.
     */
    @PostMapping("/leave-requests/{id}/approve")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> approveLeaveRequest(
            @PathVariable("id") Long id,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<LeaveRequestDto> response = groupRegistrationService.approveLeaveRequest(user, id);
        return ResponseEntity.ok(response);
    }

    /**
     * GVHD từ chối yêu cầu rút nhóm.
     */
    @PostMapping("/leave-requests/{id}/reject")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> rejectLeaveRequest(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ResolveRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        String reason = (request != null) ? request.getRejectionReason() : null;
        ApiResponse<LeaveRequestDto> response = groupRegistrationService.rejectLeaveRequest(user, id, reason);
        return ResponseEntity.ok(response);
    }

    // ==================== 4. GIAI ĐOẠN ĐANG THỰC HIỆN: EC-03 HỦY NHÓM / ĐỀ TÀI ====================

    /**
     * Nhóm trưởng gửi yêu cầu hủy nhóm / hủy đề tài lên GVHD duyệt.
     */
    @PostMapping("/groups/{groupId}/dissolve-requests")
    public ResponseEntity<ApiResponse<DissolveRequestDto>> createDissolveRequest(
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody DissolveRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        DissolveRequestDto dto = groupRegistrationService.requestDissolveApprovedGroup(user, groupId, request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã gửi yêu cầu hủy nhóm và đề tài tới Giảng viên hướng dẫn thành công!", dto));
    }

    /**
     * GVHD xem danh sách các yêu cầu hủy nhóm/đề tài đang chờ duyệt (PENDING).
     */
    @GetMapping("/dissolve-requests/pending")
    public ResponseEntity<ApiResponse<List<DissolveRequestDto>>> getPendingDissolveRequests(HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        List<DissolveRequestDto> list = groupRegistrationService.getPendingDissolveRequestsForLecturer(user);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách yêu cầu hủy nhóm thành công!", list));
    }

    /**
     * GVHD duyệt yêu cầu hủy nhóm.
     * KÍCH HOẠT EC-03: Giải tán nhóm (mọi thành viên tự do) và chuyển trạng thái đề tài (Topic) quay lại APPROVED
     * để nhóm khác có thể đăng ký.
     */
    @PostMapping("/dissolve-requests/{id}/approve")
    public ResponseEntity<ApiResponse<DissolveRequestDto>> approveDissolveRequest(
            @PathVariable("id") Long id,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<DissolveRequestDto> response = groupRegistrationService.approveDissolveRequest(user, id);
        return ResponseEntity.ok(response);
    }

    /**
     * GVHD từ chối yêu cầu hủy nhóm.
     */
    @PostMapping("/dissolve-requests/{id}/reject")
    public ResponseEntity<ApiResponse<DissolveRequestDto>> rejectDissolveRequest(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ResolveRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        String reason = (request != null) ? request.getRejectionReason() : null;
        ApiResponse<DissolveRequestDto> response = groupRegistrationService.rejectDissolveRequest(user, id, reason);
        return ResponseEntity.ok(response);
    }

    // ==================== 5. GỘP NHÓM ĐIỀU PHỐI (ADMIN / DEPARTMENT HEAD / LECTURER MERGE) ====================

    /**
     * Lấy danh sách các nhóm chưa đủ sĩ số tối thiểu trong đợt để điều phối gộp nhóm.
     */
    @GetMapping("/groups/under-enrolled")
    public ResponseEntity<ApiResponse<List<UnderEnrolledGroupDto>>> getUnderEnrolledGroups(
            @RequestParam(value = "periodId", required = false) Long periodId,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        List<UnderEnrolledGroupDto> list = groupRegistrationService.getUnderEnrolledGroups(user, periodId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách các nhóm cần gộp thành công!", list));
    }

    /**
     * Trưởng bộ môn / GVHD thực hiện gộp nhóm nguồn vào nhóm đích.
     * Chuyển tất cả thành viên sang nhóm đích, giải phóng đề tài của nhóm nguồn, cập nhật nhóm đích thành APPROVED.
     */
    @PostMapping("/groups/merge")
    public ResponseEntity<ApiResponse<UnderEnrolledGroupDto>> mergeGroups(
            @Valid @RequestBody MergeGroupsRequest request,
            HttpSession session) {
        AppUser user = getAuthenticatedUser(session);
        ApiResponse<UnderEnrolledGroupDto> response = groupRegistrationService.mergeGroups(user, request.getSourceGroupId(), request.getTargetGroupId());
        return ResponseEntity.ok(response);
    }
}
