package topicmanagement.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import topicmanagement.dto.RegistrationDtos.*;
import topicmanagement.entity.*;
import topicmanagement.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupRegistrationService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicRepository topicRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final StudentService studentService;

    public GroupRegistrationService(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            StudentGroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            TopicRepository topicRepository,
            TopicRegistrationRepository topicRegistrationRepository,
            StudentService studentService) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.topicRepository = topicRepository;
        this.topicRegistrationRepository = topicRegistrationRepository;
        this.studentService = studentService;
    }

    // ==================== HELPER AUTH METHODS ====================

    private Student getStudentFromUser(AppUser user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập!");
        }
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Không tìm thấy hồ sơ sinh viên!"));
    }

    private Lecturer getLecturerFromUser(AppUser user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập!");
        }
        return lecturerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Không tìm thấy hồ sơ giảng viên!"));
    }

    private StudentGroup getGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm sinh viên ID=" + groupId));
    }

    private boolean isGroupInDraftStage(StudentGroup group) {
        if (group == null) return true;
        // Group is in DRAFT stage if no topic or status is DRAFT/REJECTED/null
        if (group.getTopic() == null) return true;
        String status = group.getStatus();
        return status == null || "DRAFT".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status);
    }

    private int getMaxAllowedMembers(StudentGroup group) {
        if (group == null) return 5;
        if (group.getTopic() != null && group.getTopic().getMaxStudents() != null && group.getTopic().getMaxStudents() > 0) {
            return Math.min(5, Math.max(3, group.getTopic().getMaxStudents()));
        }
        if (group.getPeriod() != null && group.getPeriod().getMaxStudents() != null && group.getPeriod().getMaxStudents() > 0) {
            return Math.min(5, Math.max(3, group.getPeriod().getMaxStudents()));
        }
        return 5;
    }

    // ==================== 1. THÊM THÀNH VIÊN TRỰC TIẾP (SIMPLIFIED MEMBERSHIP) ====================

    @Transactional(rollbackFor = Exception.class)
    public InvitationResponseDto sendInvitation(AppUser user, Long groupId, String studentCode) {
        Student leader = getStudentFromUser(user);
        StudentGroup group = getGroupById(groupId);

        if (!group.isLeader(leader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Nhóm trưởng mới có quyền thêm thành viên vào nhóm!");
        }

        String error = studentService.inviteMemberByCode(group, studentCode);
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
        }

        InvitationResponseDto dto = new InvitationResponseDto();
        dto.setGroupId(groupId);
        dto.setInviteeCode(studentCode);
        dto.setStatus("ACCEPTED");
        return dto;
    }

    public InvitationResponseDto acceptInvitation(AppUser user, Long invitationId) {
        InvitationResponseDto dto = new InvitationResponseDto();
        dto.setId(invitationId);
        dto.setStatus("ACCEPTED");
        return dto;
    }

    public ApiResponse<Void> rejectInvitation(AppUser user, Long invitationId, String reason) {
        return ApiResponse.ok("Đã xử lý thành công!", null);
    }

    public ApiResponse<Void> cancelInvitation(AppUser user, Long invitationId) {
        return ApiResponse.ok("Đã xử lý thành công!", null);
    }

    public List<InvitationResponseDto> getMyPendingInvitations(AppUser user) {
        return Collections.emptyList();
    }

    public List<InvitationResponseDto> getGroupInvitations(AppUser user, Long groupId) {
        return Collections.emptyList();
    }

    // ==================== 2. GIAI ĐOẠN DRAFT (OUT NHÓM & HỦY NHÓM TỰ DO) ====================

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> leaveGroupDraft(AppUser user, Long groupId, Long targetStudentId) {
        Student currentStudent = getStudentFromUser(user);
        StudentGroup group = getGroupById(groupId);

        Long studentIdToRemove = (targetStudentId != null) ? targetStudentId : currentStudent.getId();

        // Kiểm tra xem nhóm có đang ở giai đoạn DRAFT không
        if (!isGroupInDraftStage(group)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đề tài của nhóm đã được đăng ký/phê duyệt. Thành viên muốn rút khỏi nhóm phải gửi yêu cầu xin rút lên Giảng viên hướng dẫn duyệt!");
        }

        // Quyền thực hiện: Tự rời nhóm HOẶC Nhóm trưởng đuổi thành viên
        boolean isSelf = currentStudent.getId().equals(studentIdToRemove);
        boolean isLeader = group.isLeader(currentStudent);

        if (!isSelf && !isLeader) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xóa thành viên này khỏi nhóm!");
        }

        // Nếu người muốn rời là LEADER -> Chặn lại
        if (group.isLeader(currentStudent) && isSelf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nhóm trưởng không được rời nhóm trừ khi nhượng quyền (Transfer Leadership) hoặc bấm nút Hủy nhóm (Dissolve Group)!");
        }

        groupMemberRepository.deleteByGroupIdAndStudentId(group.getId(), studentIdToRemove);
        System.out.println("[DRAFT-LEAVE] Đã xóa sinh viên ID=" + studentIdToRemove + " khỏi nhóm " + group.getGroupCode() + " (Giai đoạn DRAFT)");

        return ApiResponse.ok("Đã rời khỏi nhóm thành công!", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> transferLeadership(AppUser user, Long groupId, Long newLeaderStudentId) {
        Student currentLeader = getStudentFromUser(user);
        StudentGroup group = getGroupById(groupId);

        if (!group.isLeader(currentLeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Nhóm trưởng hiện tại mới có quyền nhượng quyền trưởng nhóm!");
        }

        if (newLeaderStudentId == null || newLeaderStudentId.equals(currentLeader.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn một thành viên khác trong nhóm để nhượng quyền!");
        }

        Student newLeader = studentRepository.findById(newLeaderStudentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên được chỉ định làm Leader mới!"));

        GroupMember newLeaderMember = groupMemberRepository.findByGroupIdAndStudentId(group.getId(), newLeader.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sinh viên được chỉ định không phải là thành viên trong nhóm!"));

        GroupMember oldLeaderMember = groupMemberRepository.findByGroupIdAndStudentId(group.getId(), currentLeader.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy thông tin nhóm trưởng hiện tại trong nhóm!"));

        // Đổi vai trò
        oldLeaderMember.setRoleInGroup("MEMBER");
        newLeaderMember.setRoleInGroup("LEADER");
        groupMemberRepository.save(oldLeaderMember);
        groupMemberRepository.save(newLeaderMember);

        group.setLeader(newLeader);
        groupRepository.save(group);

        System.out.println("[LEADER-TRANSFER] Nhóm " + group.getGroupCode() + " đã chuyển quyền Trưởng nhóm từ " + currentLeader.getStudentCode() + " sang " + newLeader.getStudentCode());

        return ApiResponse.ok("Nhượng quyền Trưởng nhóm thành công cho " + newLeader.getUser().getFullName(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> dissolveGroupDraft(AppUser user, Long groupId) {
        Student leader = getStudentFromUser(user);
        StudentGroup group = getGroupById(groupId);

        if (!group.isLeader(leader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Nhóm trưởng mới có quyền giải tán / hủy nhóm!");
        }

        if (!isGroupInDraftStage(group)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đề tài của nhóm đã được đăng ký/phê duyệt. Nhóm trưởng muốn hủy nhóm/hủy đề tài phải gửi yêu cầu lên Giảng viên hướng dẫn duyệt (EC-03)!");
        }


        // Xóa tất cả thành viên của nhóm
        groupMemberRepository.findByGroupId(group.getId()).forEach(groupMemberRepository::delete);

        // Xóa đăng ký đề tài nếu có
        topicRegistrationRepository.deleteByGroupId(group.getId());

        // Xóa nhóm
        groupRepository.delete(group);

        System.out.println("[DISSOLVE-DRAFT] Nhóm trưởng " + leader.getStudentCode() + " đã giải tán nhóm " + group.getGroupCode() + ". Mọi thành viên đã tự do.");
        return ApiResponse.ok("Đã giải tán nhóm thành công! Tất cả thành viên đã được giải phóng.", null);
    }

    // ==================== 3. RÚT KHỎI NHÓM & HỦY NHÓM (SIMPLIFIED OPERATIONS) ====================

    @Transactional(rollbackFor = Exception.class)
    public LeaveRequestDto requestLeaveApprovedGroup(AppUser user, Long groupId, String reason) {
        Student student = getStudentFromUser(user);
        StudentGroup group = getGroupById(groupId);

        if (group.isLeader(student)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhóm trưởng không thể tự rời nhóm. Vui lòng chuyển quyền nhóm trưởng hoặc giải tán nhóm!");
        }

        boolean removed = studentService.removeMember(group, student.getId());
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể rời nhóm vào thời điểm này!");
        }

        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setGroupId(groupId);
        dto.setStudentId(student.getId());
        dto.setStatus("APPROVED");
        return dto;
    }

    public List<LeaveRequestDto> getPendingLeaveRequestsForLecturer(AppUser user) {
        return Collections.emptyList();
    }

    public ApiResponse<LeaveRequestDto> approveLeaveRequest(AppUser user, Long requestId) {
        return ApiResponse.ok("Đã xử lý!", new LeaveRequestDto());
    }

    public ApiResponse<LeaveRequestDto> rejectLeaveRequest(AppUser user, Long requestId, String rejectionReason) {
        return ApiResponse.ok("Đã xử lý!", new LeaveRequestDto());
    }

    @Transactional(rollbackFor = Exception.class)
    public DissolveRequestDto requestDissolveApprovedGroup(AppUser user, Long groupId, String reason) {
        Student leader = getStudentFromUser(user);
        StudentGroup group = getGroupById(groupId);

        if (!group.isLeader(leader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Nhóm trưởng mới có quyền giải tán nhóm!");
        }

        if (group.getTopic() != null) {
            Topic topic = group.getTopic();
            topic.setStatus("APPROVED");
            topic.setAdvisorScore(null);
            topic.setReviewerScore(null);
            topic.setCouncilScore(null);
            topic.setFinalScore(null);
            topic.setIsFinalized(false);
            topicRepository.save(topic);
        }

        topicRegistrationRepository.deleteByGroupId(group.getId());
        groupMemberRepository.findByGroupId(group.getId()).forEach(groupMemberRepository::delete);
        group.setTopic(null);
        group.setStatus("DISSOLVED");
        groupRepository.save(group);

        DissolveRequestDto dto = new DissolveRequestDto();
        dto.setGroupId(groupId);
        dto.setStatus("APPROVED");
        return dto;
    }

    public List<DissolveRequestDto> getPendingDissolveRequestsForLecturer(AppUser user) {
        return Collections.emptyList();
    }

    public ApiResponse<DissolveRequestDto> approveDissolveRequest(AppUser user, Long requestId) {
        return ApiResponse.ok("Đã phê duyệt hủy nhóm!", new DissolveRequestDto());
    }

    public ApiResponse<DissolveRequestDto> rejectDissolveRequest(AppUser user, Long requestId, String rejectionReason) {
        return ApiResponse.ok("Đã từ chối hủy nhóm!", new DissolveRequestDto());
    }

    // ==================== 5. GỘP NHÓM ĐIỀU PHỐI (ADMIN / DEPARTMENT HEAD / LECTURER MERGE) ====================

    public List<UnderEnrolledGroupDto> getUnderEnrolledGroups(AppUser user, Long periodId) {
        if (user == null || user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập!");
        }
        String role = user.getRole().getCode();
        if (!"ADMIN".equalsIgnoreCase(role) && !"DEPARTMENT_HEAD".equalsIgnoreCase(role) && !"LECTURER".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản trị viên, Trưởng bộ môn hoặc Giảng viên mới có quyền xem danh sách nhóm cần gộp!");
        }

        List<StudentGroup> allGroups = (periodId != null) 
                ? groupRepository.findByPeriodId(periodId) 
                : groupRepository.findAll();

        return allGroups.stream()
                .filter(g -> {
                    String status = g.getStatus() != null ? g.getStatus() : "";
                    if ("DISSOLVED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status) || "DEFENDING".equalsIgnoreCase(status)) {
                        return false;
                    }
                    int memberCount = g.getMembers() != null ? g.getMembers().size() : 0;
                    int minStudents = (g.getPeriod() != null && g.getPeriod().getMinStudents() != null) ? g.getPeriod().getMinStudents() : 2;
                    return "PENDING_MEMBER".equalsIgnoreCase(status) || "DRAFT".equalsIgnoreCase(status) || memberCount < minStudents;
                })
                .map(this::mapToUnderEnrolledDto)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<UnderEnrolledGroupDto> mergeGroups(AppUser user, Long sourceGroupId, Long targetGroupId) {
        if (user == null || user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập!");
        }
        String role = user.getRole().getCode();
        if (!"ADMIN".equalsIgnoreCase(role) && !"DEPARTMENT_HEAD".equalsIgnoreCase(role) && !"LECTURER".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Quản trị viên, Trưởng bộ môn hoặc Giảng viên mới có quyền thực hiện gộp nhóm!");
        }

        if (sourceGroupId == null || targetGroupId == null || sourceGroupId.equals(targetGroupId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhóm nguồn và nhóm đích phải khác nhau và không được để trống!");
        }

        StudentGroup sourceGroup = getGroupById(sourceGroupId);
        StudentGroup targetGroup = getGroupById(targetGroupId);
        Set<Long> studentIds = new TreeSet<>();
        for (GroupMember member : groupMemberRepository.findByGroupId(sourceGroupId)) studentIds.add(member.getStudent().getId());
        for (GroupMember member : groupMemberRepository.findByGroupId(targetGroupId)) studentIds.add(member.getStudent().getId());
        for (Long id : studentIds) studentRepository.findForMembership(id).orElseThrow();
        StudentGroup first = studentService.lockGroupForMembership(Math.min(sourceGroupId, targetGroupId));
        StudentGroup second = studentService.lockGroupForMembership(Math.max(sourceGroupId, targetGroupId));
        sourceGroup = sourceGroupId.equals(first.getId()) ? first : second;
        targetGroup = targetGroupId.equals(first.getId()) ? first : second;

        // Kiểm tra quyền nếu là Giảng viên: phải là GVHD của một trong 2 nhóm
        if ("LECTURER".equalsIgnoreCase(role)) {
            Lecturer lec = getLecturerFromUser(user);
            boolean isSourceSupervisor = sourceGroup.getTopic() != null && sourceGroup.getTopic().isSupervisorOrCoSupervisor(lec);
            boolean isTargetSupervisor = targetGroup.getTopic() != null && targetGroup.getTopic().isSupervisorOrCoSupervisor(lec);
            if (!isSourceSupervisor && !isTargetSupervisor) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không phải Giảng viên hướng dẫn của nhóm nguồn hoặc nhóm đích!");
            }
        }

        // Kiểm tra đợt đăng ký phải cùng một đợt
        if (sourceGroup.getPeriod() != null && targetGroup.getPeriod() != null) {
            if (!sourceGroup.getPeriod().getId().equals(targetGroup.getPeriod().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể gộp hai nhóm thuộc hai Đợt đăng ký khác nhau!");
            }
        }

        // 1. Kiểm tra sĩ số hợp nhất (3 - 5 SV theo Rubric)
        List<GroupMember> sourceMembers = groupMemberRepository.findByGroupId(sourceGroup.getId());
        List<GroupMember> targetMembers = groupMemberRepository.findByGroupId(targetGroup.getId());

        int totalMembers = sourceMembers.size() + targetMembers.size();
        int maxAllowed = getMaxAllowedMembers(targetGroup);

        if (totalMembers > maxAllowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể gộp nhóm: Tổng sĩ số (" + totalMembers + " SV) vượt quá giới hạn tối đa cho phép (" + maxAllowed + " SV) của nhóm đích!");
        }

        // 2. Chuyển toàn bộ thành viên từ nhóm nguồn sang nhóm đích làm MEMBER
        for (GroupMember sm : sourceMembers) {
            sm.setGroup(targetGroup);
            sm.setRoleInGroup("MEMBER"); // Nhóm trưởng cũ của nguồn chuyển thành MEMBER ở nhóm đích
            groupMemberRepository.save(sm);
        }


        // 4. Giải phóng đề tài của nhóm nguồn (nếu có) quay về APPROVED
        if (sourceGroup.getTopic() != null) {
            Topic sourceTopic = sourceGroup.getTopic();
            // Nếu đề tài nhóm nguồn khác đề tài nhóm đích thì trả lại APPROVED để nhóm khác đăng ký
            if (targetGroup.getTopic() == null || !sourceTopic.getId().equals(targetGroup.getTopic().getId())) {
                sourceTopic.setStatus("APPROVED");
                sourceTopic.setAdvisorScore(null);
                sourceTopic.setReviewerScore(null);
                sourceTopic.setCouncilScore(null);
                sourceTopic.setFinalScore(null);
                sourceTopic.setIsFinalized(false);
                topicRepository.save(sourceTopic);
                System.out.println("[MERGE-GROUPS] Đề tài '" + sourceTopic.getTitle() + "' của nhóm nguồn đã được giải phóng quay lại APPROVED.");
            }
            topicRegistrationRepository.deleteByGroupId(sourceGroup.getId());
        }

        // 5. Cập nhật trạng thái nhóm đích sau khi đã đủ người
        int newTargetCount = groupMemberRepository.findByGroupId(targetGroup.getId()).size();
        int minStudents = (targetGroup.getPeriod() != null && targetGroup.getPeriod().getMinStudents() != null) ? targetGroup.getPeriod().getMinStudents() : 2;

        if (newTargetCount >= minStudents) {
            if (targetGroup.getTopic() != null) {
                targetGroup.setStatus("APPROVED");
            } else {
                targetGroup.setStatus("DRAFT");
            }
        }
        groupRepository.save(targetGroup);

        // Giữ nhóm nguồn để bảo toàn lịch sử phiếu điểm.
        sourceGroup.setTopic(null);
        sourceGroup.setStatus("DISSOLVED");
        groupRepository.save(sourceGroup);

        System.out.println("[MERGE-GROUPS] Đã gộp thành công nhóm '" + sourceGroup.getGroupName() + "' vào nhóm '" + targetGroup.getGroupName()
                + "'. Sĩ số nhóm đích hiện tại: " + newTargetCount + " SV.");

        return ApiResponse.ok("Gộp nhóm thành công! Nhóm '" + targetGroup.getGroupName() + "' hiện có " + newTargetCount + " thành viên.", mapToUnderEnrolledDto(targetGroup));
    }

    private UnderEnrolledGroupDto mapToUnderEnrolledDto(StudentGroup g) {
        UnderEnrolledGroupDto dto = new UnderEnrolledGroupDto();
        dto.setGroupId(g.getId());
        dto.setGroupCode(g.getGroupCode());
        dto.setGroupName(g.getGroupName());
        dto.setStatus(g.getStatus());
        if (g.getPeriod() != null) {
            dto.setPeriodId(g.getPeriod().getId());
            dto.setPeriodName(g.getPeriod().getName());
            dto.setMinStudents(g.getPeriod().getMinStudents());
            dto.setMaxStudents(g.getPeriod().getMaxStudents());
        }
        List<GroupMember> members = groupMemberRepository.findByGroupId(g.getId());
        dto.setCurrentMembers(members.size());
        dto.setMemberNames(members.stream()
                .map(m -> m.getStudent().getUser() != null ? m.getStudent().getUser().getFullName() + " (" + m.getStudent().getStudentCode() + ")" : m.getStudent().getStudentCode())
                .collect(Collectors.toList()));
        if (g.getLeader() != null) {
            dto.setLeaderCode(g.getLeader().getStudentCode());
            dto.setLeaderName(g.getLeader().getUser() != null ? g.getLeader().getUser().getFullName() : "");
        }
        if (g.getTopic() != null) {
            dto.setTopicId(g.getTopic().getId());
            dto.setTopicCode(g.getTopic().getCode());
            dto.setTopicTitle(g.getTopic().getTitle());
        }
        return dto;
    }


}
