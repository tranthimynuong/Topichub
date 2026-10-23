package topicmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public class RegistrationDtos {

    public static class InviteMemberRequest {
        @NotBlank(message = "Mã sinh viên không được để trống")
        private String studentCode;

        public InviteMemberRequest() {}
        public InviteMemberRequest(String studentCode) { this.studentCode = studentCode; }

        public String getStudentCode() { return studentCode; }
        public void setStudentCode(String studentCode) { this.studentCode = studentCode; }
    }

    public static class TransferLeadershipRequest {
        @NotNull(message = "ID sinh viên được nhượng quyền không được để trống")
        private Long newLeaderStudentId;

        public TransferLeadershipRequest() {}
        public TransferLeadershipRequest(Long newLeaderStudentId) { this.newLeaderStudentId = newLeaderStudentId; }

        public Long getNewLeaderStudentId() { return newLeaderStudentId; }
        public void setNewLeaderStudentId(Long newLeaderStudentId) { this.newLeaderStudentId = newLeaderStudentId; }
    }

    public static class LeaveRequest {
        @NotBlank(message = "Lý do xin rút nhóm không được để trống")
        private String reason;

        public LeaveRequest() {}
        public LeaveRequest(String reason) { this.reason = reason; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class DissolveRequest {
        @NotBlank(message = "Lý do yêu cầu hủy nhóm / đề tài không được để trống")
        private String reason;

        public DissolveRequest() {}
        public DissolveRequest(String reason) { this.reason = reason; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ResolveRequest {
        private String rejectionReason;

        public ResolveRequest() {}
        public ResolveRequest(String rejectionReason) { this.rejectionReason = rejectionReason; }

        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    }

    public static class MergeGroupsRequest {
        @NotNull(message = "ID nhóm nguồn (sourceGroupId) không được để trống")
        private Long sourceGroupId;

        @NotNull(message = "ID nhóm đích (targetGroupId) không được để trống")
        private Long targetGroupId;

        public MergeGroupsRequest() {}
        public MergeGroupsRequest(Long sourceGroupId, Long targetGroupId) {
            this.sourceGroupId = sourceGroupId;
            this.targetGroupId = targetGroupId;
        }

        public Long getSourceGroupId() { return sourceGroupId; }
        public void setSourceGroupId(Long sourceGroupId) { this.sourceGroupId = sourceGroupId; }
        public Long getTargetGroupId() { return targetGroupId; }
        public void setTargetGroupId(Long targetGroupId) { this.targetGroupId = targetGroupId; }
    }

    public static class UnderEnrolledGroupDto {
        private Long groupId;
        private String groupCode;
        private String groupName;
        private String status;
        private Long periodId;
        private String periodName;
        private Integer currentMembers;
        private Integer minStudents;
        private Integer maxStudents;
        private Long topicId;
        private String topicCode;
        private String topicTitle;
        private String leaderName;
        private String leaderCode;
        private List<String> memberNames;

        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getGroupCode() { return groupCode; }
        public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getPeriodId() { return periodId; }
        public void setPeriodId(Long periodId) { this.periodId = periodId; }
        public String getPeriodName() { return periodName; }
        public void setPeriodName(String periodName) { this.periodName = periodName; }
        public Integer getCurrentMembers() { return currentMembers; }
        public void setCurrentMembers(Integer currentMembers) { this.currentMembers = currentMembers; }
        public Integer getMinStudents() { return minStudents; }
        public void setMinStudents(Integer minStudents) { this.minStudents = minStudents; }
        public Integer getMaxStudents() { return maxStudents; }
        public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
        public Long getTopicId() { return topicId; }
        public void setTopicId(Long topicId) { this.topicId = topicId; }
        public String getTopicCode() { return topicCode; }
        public void setTopicCode(String topicCode) { this.topicCode = topicCode; }
        public String getTopicTitle() { return topicTitle; }
        public void setTopicTitle(String topicTitle) { this.topicTitle = topicTitle; }
        public String getLeaderName() { return leaderName; }
        public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
        public String getLeaderCode() { return leaderCode; }
        public void setLeaderCode(String leaderCode) { this.leaderCode = leaderCode; }
        public List<String> getMemberNames() { return memberNames; }
        public void setMemberNames(List<String> memberNames) { this.memberNames = memberNames; }
    }

    public static class InvitationResponseDto {
        private Long id;
        private Long groupId;
        private String groupCode;
        private String groupName;
        private Long inviteeStudentId;
        private String inviteeCode;
        private String inviteeName;
        private Long invitedByStudentId;
        private String invitedByCode;
        private String invitedByName;
        private String status;
        private String rejectionReason;
        private OffsetDateTime createdAt;
        private OffsetDateTime respondedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getGroupCode() { return groupCode; }
        public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public Long getInviteeStudentId() { return inviteeStudentId; }
        public void setInviteeStudentId(Long inviteeStudentId) { this.inviteeStudentId = inviteeStudentId; }
        public String getInviteeCode() { return inviteeCode; }
        public void setInviteeCode(String inviteeCode) { this.inviteeCode = inviteeCode; }
        public String getInviteeName() { return inviteeName; }
        public void setInviteeName(String inviteeName) { this.inviteeName = inviteeName; }
        public Long getInvitedByStudentId() { return invitedByStudentId; }
        public void setInvitedByStudentId(Long invitedByStudentId) { this.invitedByStudentId = invitedByStudentId; }
        public String getInvitedByCode() { return invitedByCode; }
        public void setInvitedByCode(String invitedByCode) { this.invitedByCode = invitedByCode; }
        public String getInvitedByName() { return invitedByName; }
        public void setInvitedByName(String invitedByName) { this.invitedByName = invitedByName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
        public OffsetDateTime getRespondedAt() { return respondedAt; }
        public void setRespondedAt(OffsetDateTime respondedAt) { this.respondedAt = respondedAt; }
    }

    public static class LeaveRequestDto {
        private Long id;
        private Long groupId;
        private String groupCode;
        private String groupName;
        private Long studentId;
        private String studentCode;
        private String studentName;
        private String reason;
        private String status;
        private Long supervisorId;
        private String supervisorName;
        private String rejectionReason;
        private OffsetDateTime createdAt;
        private OffsetDateTime resolvedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getGroupCode() { return groupCode; }
        public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public String getStudentCode() { return studentCode; }
        public void setStudentCode(String studentCode) { this.studentCode = studentCode; }
        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getSupervisorId() { return supervisorId; }
        public void setSupervisorId(Long supervisorId) { this.supervisorId = supervisorId; }
        public String getSupervisorName() { return supervisorName; }
        public void setSupervisorName(String supervisorName) { this.supervisorName = supervisorName; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
        public OffsetDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    }

    public static class DissolveRequestDto {
        private Long id;
        private Long groupId;
        private String groupCode;
        private String groupName;
        private Long topicId;
        private String topicCode;
        private String topicTitle;
        private Long requestedByStudentId;
        private String requestedByCode;
        private String requestedByName;
        private String reason;
        private String status;
        private Long supervisorId;
        private String supervisorName;
        private String rejectionReason;
        private OffsetDateTime createdAt;
        private OffsetDateTime resolvedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getGroupCode() { return groupCode; }
        public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public Long getTopicId() { return topicId; }
        public void setTopicId(Long topicId) { this.topicId = topicId; }
        public String getTopicCode() { return topicCode; }
        public void setTopicCode(String topicCode) { this.topicCode = topicCode; }
        public String getTopicTitle() { return topicTitle; }
        public void setTopicTitle(String topicTitle) { this.topicTitle = topicTitle; }
        public Long getRequestedByStudentId() { return requestedByStudentId; }
        public void setRequestedByStudentId(Long requestedByStudentId) { this.requestedByStudentId = requestedByStudentId; }
        public String getRequestedByCode() { return requestedByCode; }
        public void setRequestedByCode(String requestedByCode) { this.requestedByCode = requestedByCode; }
        public String getRequestedByName() { return requestedByName; }
        public void setRequestedByName(String requestedByName) { this.requestedByName = requestedByName; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getSupervisorId() { return supervisorId; }
        public void setSupervisorId(Long supervisorId) { this.supervisorId = supervisorId; }
        public String getSupervisorName() { return supervisorName; }
        public void setSupervisorName(String supervisorName) { this.supervisorName = supervisorName; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
        public OffsetDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    }

    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private String warning;

        public ApiResponse() {}
        public ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        public ApiResponse(boolean success, String message, T data, String warning) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.warning = warning;
        }

        public static <T> ApiResponse<T> ok(String message, T data) {
            return new ApiResponse<>(true, message, data);
        }

        public static <T> ApiResponse<T> okWithWarning(String message, T data, String warning) {
            return new ApiResponse<>(true, message, data, warning);
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        public String getWarning() { return warning; }
        public void setWarning(String warning) { this.warning = warning; }
    }
}
