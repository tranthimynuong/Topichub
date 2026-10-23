package topicmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import topicmanagement.entity.*;
import topicmanagement.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LecturerService {

    private final LecturerRepository lecturerRepository;
    private final TopicRepository topicRepository;
    private final SubmissionRepository submissionRepository;
    private final DepartmentRepository departmentRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final CouncilRepository councilRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;

    public LecturerService(
            LecturerRepository lecturerRepository,
            TopicRepository topicRepository,
            SubmissionRepository submissionRepository,
            DepartmentRepository departmentRepository,
            RegistrationPeriodRepository periodRepository,
            StudentGroupRepository studentGroupRepository,
            CouncilRepository councilRepository,
            TopicRegistrationRepository topicRegistrationRepository) {
        this.lecturerRepository = lecturerRepository;
        this.topicRepository = topicRepository;
        this.submissionRepository = submissionRepository;
        this.departmentRepository = departmentRepository;
        this.periodRepository = periodRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.councilRepository = councilRepository;
        this.topicRegistrationRepository = topicRegistrationRepository;
    }

    @Transactional(readOnly = true)
    public Lecturer getLecturerByUser(AppUser user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return lecturerRepository.findByUserId(user.getId()).orElse(null);
    }

    public List<Lecturer> getAllLecturers() {
        return lecturerRepository.findAll();
    }

    public List<Lecturer> getOtherLecturers(Long currentLecturerId) {
        if (currentLecturerId == null) {
            return lecturerRepository.findAll();
        }
        Lecturer current = lecturerRepository.findById(currentLecturerId).orElse(null);
        Long deptId = current != null && current.getDepartment() != null ? current.getDepartment().getId() : null;
        return lecturerRepository.findAll().stream()
                .filter(l -> !l.getId().equals(currentLecturerId))
                .filter(l -> deptId == null || (l.getDepartment() != null && deptId.equals(l.getDepartment().getId())))
                .toList();
    }

    public List<Topic> getTopicsByLecturer(Lecturer lecturer) {
        if (lecturer == null || lecturer.getId() == null) {
            return Collections.emptyList();
        }
        List<Topic> topics = topicRepository.findBySupervisorOrCoSupervisorId(lecturer.getId());
        return topics.stream()
                .sorted(Comparator
                        .comparing((Topic t) -> "PENDING".equalsIgnoreCase(t.getStatus()) ? 0 : 1)
                        .thenComparing(Topic::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Topic::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public List<Topic> getReviewerTopics(Lecturer lecturer) {
        if (lecturer == null || lecturer.getId() == null) {
            return Collections.emptyList();
        }
        return topicRepository.findByReviewerId(lecturer.getId());
    }

    public List<Council> getCouncilsForLecturer(Lecturer lecturer) {
        if (lecturer == null || lecturer.getId() == null) {
            return Collections.emptyList();
        }
        return councilRepository.findByLecturerId(lecturer.getId());
    }

    public Topic getTopicById(Long id) {
        if (id == null) {
            return null;
        }
        return topicRepository.findById(id).orElse(null);
    }

    @Transactional
    public Topic createTopic(Lecturer supervisor, Long periodId, Long coSupervisorId, Topic topic) {
        topic.setSupervisor(supervisor);
        if (supervisor != null && supervisor.getDepartment() != null) {
            topic.setDepartment(supervisor.getDepartment());
        }

        // BUG-01 FIX: Kiểm tra đợt đăng ký có đang mở cho giảng viên đề xuất không
        if (periodId != null) {
            RegistrationPeriod period = periodRepository.findById(periodId).orElse(null);
            if (period != null && !period.isLecturerProposalOpen()) {
                throw new IllegalStateException("Đợt '" + period.getName()
                        + "' hiện không trong giai đoạn nhận đề xuất đề tài từ giảng viên!");
            }
            topic.setPeriod(period);
            if (topic.getDepartment() == null && period != null && period.getDepartment() != null) {
                topic.setDepartment(period.getDepartment());
            }
        }

        if (coSupervisorId != null && (supervisor == null || !coSupervisorId.equals(supervisor.getId()))) {
            topic.setCoSupervisor(lecturerRepository.findById(coSupervisorId).orElse(null));
        }

        if (topic.getCode() == null || topic.getCode().trim().isEmpty()) {
            topic.setCode("DT-" + System.currentTimeMillis() % 100000);
        }

        topic.setStatus("PENDING");
        topic.setCreatedAt(OffsetDateTime.now());

        Topic saved = topicRepository.save(topic);
        System.out.println(
                "[DB-LECTURER] Đã lưu đề tài đề xuất mới: '" + saved.getTitle() + "' (ID=" + saved.getId() + ")");
        return saved;
    }

    @Transactional
    public Topic updateTopic(Lecturer currentLecturer, Long topicId, Long periodId, Long coSupervisorId, Topic topicData) {
        if (topicId == null || currentLecturer == null) {
            throw new IllegalArgumentException("Thông tin không hợp lệ.");
        }
        Topic topic = topicRepository.findById(topicId).orElse(null);
        if (topic == null) {
            throw new IllegalArgumentException("Không tìm thấy đề tài ID=" + topicId);
        }

        // Security check: Only supervisor or co-supervisor can edit
        if (!topic.isSupervisorOrCoSupervisor(currentLecturer)) {
            throw new IllegalStateException("Bạn không có quyền chỉnh sửa đề tài này (không phải GVHD)!");
        }

        // Kiểm tra các trường cốt lõi có bị thay đổi không
        String newTitle = (topicData.getTitle() != null) ? topicData.getTitle().trim() : null;
        boolean isTitleChanged = newTitle != null && !newTitle.isEmpty() && !newTitle.equals(topic.getTitle());
        Long currentPeriodId = (topic.getPeriod() != null) ? topic.getPeriod().getId() : null;
        boolean isPeriodChanged = (periodId != null) && !periodId.equals(currentPeriodId);
        boolean wasApproved = "APPROVED".equalsIgnoreCase(topic.getStatus());

        // Check if student group already assigned and active
        List<StudentGroup> groups = studentGroupRepository.findByTopicId(topicId);
        boolean hasActiveGroup = groups.stream().anyMatch(g -> g.getStatus() == null
                || (!"REJECTED".equalsIgnoreCase(g.getStatus()) && !"DRAFT".equalsIgnoreCase(g.getStatus())));

        if (hasActiveGroup && wasApproved) {
            if (isTitleChanged || isPeriodChanged) {
                throw new IllegalStateException("Không thể chỉnh sửa đề tài đã được duyệt");
            }
            // If already approved with active group, allow updating description and requirements
            topic.setDescription(topicData.getDescription());
            topic.setRequirements(topicData.getRequirements());
        } else {
            // Fully updateable
            if (newTitle != null && !newTitle.isEmpty()) {
                topic.setTitle(newTitle);
            }
            if (topicData.getCode() != null && !topicData.getCode().trim().isEmpty()) {
                topic.setCode(topicData.getCode().trim());
            }
            if (periodId != null) {
                RegistrationPeriod period = periodRepository.findById(periodId).orElse(null);
                topic.setPeriod(period);
            }
            if (coSupervisorId != null && !coSupervisorId.equals(currentLecturer.getId())) {
                topic.setCoSupervisor(lecturerRepository.findById(coSupervisorId).orElse(null));
            } else if (coSupervisorId == null) {
                topic.setCoSupervisor(null);
            }
            if (topicData.getMaxStudents() != null && topicData.getMaxStudents() > 0) {
                topic.setMaxStudents(Math.min(5, Math.max(3, topicData.getMaxStudents())));
            }
            topic.setDescription(topicData.getDescription());
            topic.setRequirements(topicData.getRequirements());

            // Nếu chỉnh sửa các trường cốt lõi (tên, đợt) của một đề tài đã duyệt, tự động hạ về PENDING
            if (wasApproved && (isTitleChanged || isPeriodChanged)) {
                topic.setStatus("PENDING");
                topic.setRejectionReason(null);
                System.out.println("[DB-LECTURER] Đề tài ID=" + topic.getId() + " đã thay đổi trường cốt lõi (tên/đợt) sau khi APPROVED -> Tự động chuyển về PENDING để Trưởng khoa thẩm định lại.");
            } else if ("REJECTED".equalsIgnoreCase(topic.getStatus())) {
                // If it was REJECTED, resubmitting sets it back to PENDING and clears rejection reason
                topic.setStatus("PENDING");
                topic.setRejectionReason(null);
            }
        }

        Topic saved = topicRepository.save(topic);
        System.out.println("[DB-LECTURER] Đã cập nhật đề tài ID=" + saved.getId() + " (Status=" + saved.getStatus() + ")");
        return saved;
    }

    @Transactional
    public boolean deleteTopic(Long topicId, Lecturer currentLecturer) {
        if (topicId == null || currentLecturer == null) {
            return false;
        }
        Topic topic = topicRepository.findById(topicId).orElse(null);
        if (topic == null) {
            return false;
        }
        if (!topic.isSupervisorOrCoSupervisor(currentLecturer)) {
            throw new IllegalStateException("Bạn không có quyền xóa đề tài này (không phải GVHD)!");
        }

        // Điều kiện 1: Chặn xóa nếu đề tài đã ở trạng thái APPROVED
        if ("APPROVED".equalsIgnoreCase(topic.getStatus())) {
            throw new IllegalStateException("Không thể xóa đề tài đã ở trạng thái phê duyệt (APPROVED)!");
        }

        // Điều kiện 2: Chặn xóa nếu studentGroupRepository.findByTopicId(topicId) đang có nhóm hoạt động
        List<StudentGroup> groups = studentGroupRepository.findByTopicId(topicId);
        boolean hasActiveGroup = groups.stream().anyMatch(g -> g.getStatus() == null
                || (!"REJECTED".equalsIgnoreCase(g.getStatus()) && !"DRAFT".equalsIgnoreCase(g.getStatus())));
        if (hasActiveGroup) {
            throw new IllegalStateException("Không thể xóa đề tài đang có nhóm sinh viên hoạt động!");
        }

        topicRepository.delete(topic);
        System.out.println("[DB-LECTURER] Đã xóa đề tài ID=" + topicId);
        return true;
    }

    public List<StudentGroup> getGroupsForLecturerTopics(Lecturer lecturer) {
        if (lecturer == null || lecturer.getId() == null) {
            return Collections.emptyList();
        }
        List<Topic> myTopics = getTopicsByLecturer(lecturer);
        List<StudentGroup> groups = new ArrayList<>();
        for (Topic t : myTopics) {
            groups.addAll(studentGroupRepository.findByTopicId(t.getId()));
        }
        return groups;
    }

    @Transactional
    public boolean approveGroupTopic(Long groupId, String status, Lecturer currentLecturer) {
        StudentGroup group = studentGroupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        // Guard: Chỉ GVHD/GVHD phụ mới được duyệt
        if (group.getTopic() != null && currentLecturer != null
                && !group.getTopic().isSupervisorOrCoSupervisor(currentLecturer)) {
            System.err.println("[SECURITY] GV ID=" + currentLecturer.getId()
                    + " cố duyệt nhóm của đề tài không phải mình hướng dẫn!");
            return false;
        }

        if ("APPROVED".equalsIgnoreCase(status)) {
            int memberCount = (group.getMembers() != null) ? group.getMembers().size() : 0;
            if (memberCount < 1 || memberCount > 5) {
                System.err.println("[VALIDATION] Không thể duyệt nhóm ID=" + groupId + " vì số thành viên (" + memberCount + ") không hợp lệ!");
                throw new IllegalStateException("Không thể duyệt nhóm: Nhóm hiện có " + memberCount + " sinh viên. Số lượng sinh viên không hợp lệ (tối đa 5 SV)!");
            }
        }

        if ("REJECTED".equalsIgnoreCase(status)) {
            topicRegistrationRepository.deleteByGroupId(group.getId());
            group.setTopic(null);
            group.setStatus("DRAFT");
        } else {
            group.setStatus(status);
        }
        studentGroupRepository.save(group);
        System.out.println("[DB-LECTURER] Cập nhật trạng thái nhóm ID=" + groupId + " -> " + status);
        return true;
    }

    public List<Submission> getSubmissionsByLecturer(Lecturer lecturer) {
        if (lecturer == null || lecturer.getId() == null) {
            return Collections.emptyList();
        }
        return submissionRepository.findBySupervisorOrCoSupervisorId(lecturer.getId());
    }

    @Transactional
    public boolean gradeSubmission(Long submissionId, Double grade, String feedback, Lecturer currentLecturer) {
        if (submissionId == null) {
            return false;
        }
        if (grade == null || !Double.isFinite(grade) || grade < 0.0 || grade > 10.0) {
            System.err.println("[DB-LECTURER] Điểm bài nộp không hợp lệ: " + grade + " (Điểm phải từ 0.0 đến 10.0)!");
            return false;
        }
        Optional<Submission> opt = submissionRepository.findById(submissionId);
        if (opt.isPresent()) {
            Submission sub = opt.get();
            // Guard: Chỉ GVHD/GVHD phụ mới được chấm bài nộp
            if (sub.getTopic() != null && currentLecturer != null
                    && !sub.getTopic().isSupervisorOrCoSupervisor(currentLecturer)) {
                System.err.println("[SECURITY] GV ID=" + currentLecturer.getId()
                        + " cố chấm bài nộp của đề tài không phải mình hướng dẫn!");
                return false;
            }
            sub.setGrade(grade);
            sub.setFeedback(feedback);
            submissionRepository.save(sub);
            System.out.println("[DB-LECTURER] Đã lưu nhận xét và điểm bài nộp ID=" + submissionId);
            return true;
        }
        return false;
    }

    public List<Submission> getSubmissionsByTopic(Long topicId) {
        if (topicId == null) {
            return Collections.emptyList();
        }
        return submissionRepository.findByTopicId(topicId);
    }

    public List<RegistrationPeriod> getOpenProposalPeriods(Lecturer lecturer) {
        List<RegistrationPeriod> list;
        if (lecturer != null && lecturer.getDepartment() != null) {
            list = periodRepository.findByDepartmentId(lecturer.getDepartment().getId());
        } else {
            list = periodRepository.findByIsActiveTrueOrderByIdDesc();
        }
        return list.stream()
                .filter(RegistrationPeriod::isLecturerProposalOpen)
                .toList();
    }

    public List<RegistrationPeriod> getOpenProposalPeriods() {
        return getOpenProposalPeriods(null);
    }

    public List<RegistrationPeriod> getAllPeriods() {
        return periodRepository.findAll();
    }

    public List<RegistrationPeriod> getPeriodsForLecturer(Lecturer lecturer) {
        if (lecturer != null && lecturer.getDepartment() != null) {
            return periodRepository.findByDepartmentId(lecturer.getDepartment().getId());
        }
        return periodRepository.findByIsActiveTrueOrderByIdDesc();
    }

    public static class ProposalProgressDto {

        private RegistrationPeriod period;
        private long proposedCount;
        private long approvedCount;
        private long pendingCount;
        private long rejectedCount;
        private int minRequired;
        private int maxQuota;
        private boolean targetReached;
        private int remainingNeeded;
        private int progressPercent;
        private long daysRemaining;

        public ProposalProgressDto() {
        }

        public RegistrationPeriod getPeriod() {
            return period;
        }

        public void setPeriod(RegistrationPeriod period) {
            this.period = period;
        }

        public long getProposedCount() {
            return proposedCount;
        }

        public void setProposedCount(long proposedCount) {
            this.proposedCount = proposedCount;
        }

        public long getApprovedCount() {
            return approvedCount;
        }

        public void setApprovedCount(long approvedCount) {
            this.approvedCount = approvedCount;
        }

        public long getPendingCount() {
            return pendingCount;
        }

        public void setPendingCount(long pendingCount) {
            this.pendingCount = pendingCount;
        }

        public long getRejectedCount() {
            return rejectedCount;
        }

        public void setRejectedCount(long rejectedCount) {
            this.rejectedCount = rejectedCount;
        }

        public int getMinRequired() {
            return minRequired;
        }

        public void setMinRequired(int minRequired) {
            this.minRequired = minRequired;
        }

        public int getMaxQuota() {
            return maxQuota;
        }

        public void setMaxQuota(int maxQuota) {
            this.maxQuota = maxQuota;
        }

        public boolean isTargetReached() {
            return targetReached;
        }

        public void setTargetReached(boolean targetReached) {
            this.targetReached = targetReached;
        }

        public int getRemainingNeeded() {
            return remainingNeeded;
        }

        public void setRemainingNeeded(int remainingNeeded) {
            this.remainingNeeded = remainingNeeded;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public long getDaysRemaining() {
            return daysRemaining;
        }

        public void setDaysRemaining(long daysRemaining) {
            this.daysRemaining = daysRemaining;
        }
    }

    public List<ProposalProgressDto> getProposalProgressList(Lecturer lecturer) {
        List<RegistrationPeriod> openPeriods = getOpenProposalPeriods(lecturer);
        if (openPeriods.isEmpty()) {
            return Collections.emptyList();
        }

        List<Topic> myTopics = getTopicsByLecturer(lecturer);
        List<ProposalProgressDto> list = new ArrayList<>();

        for (RegistrationPeriod p : openPeriods) {
            ProposalProgressDto dto = new ProposalProgressDto();
            dto.setPeriod(p);

            List<Topic> periodTopics = myTopics.stream()
                    .filter(t -> t.getPeriod() != null && t.getPeriod().getId().equals(p.getId()))
                    .toList();

            long approved = periodTopics.stream().filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus())).count();
            long pending = periodTopics.stream().filter(t -> "PENDING".equalsIgnoreCase(t.getStatus())).count();
            long rejected = periodTopics.stream().filter(t -> "REJECTED".equalsIgnoreCase(t.getStatus())).count();
            long validProposed = approved + pending;

            int minReq = p.getMinTopicsPerLecturer() != null ? p.getMinTopicsPerLecturer() : 3;
            int maxQ = (lecturer != null && lecturer.getMaxTopicsQuota() != null) ? lecturer.getMaxTopicsQuota() : 5;

            dto.setApprovedCount(approved);
            dto.setPendingCount(pending);
            dto.setRejectedCount(rejected);
            dto.setProposedCount(validProposed);
            dto.setMinRequired(minReq);
            dto.setMaxQuota(maxQ);
            dto.setTargetReached(validProposed >= minReq);
            dto.setRemainingNeeded(Math.max(0, minReq - (int) validProposed));

            int pct = (minReq > 0) ? (int) Math.min(100, (validProposed * 100) / minReq) : 100;
            dto.setProgressPercent(pct);

            long daysLeft = 0;
            if (p.getLecturerProposalEnd() != null) {
                java.time.Duration diff = java.time.Duration.between(OffsetDateTime.now(), p.getLecturerProposalEnd());
                daysLeft = Math.max(0, diff.toDays());
            }
            dto.setDaysRemaining(daysLeft);

            list.add(dto);
        }

        return list;
    }
}
