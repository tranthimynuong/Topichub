package topicmanagement.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import topicmanagement.entity.*;
import topicmanagement.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final TopicRepository topicRepository;
    private final StudentGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SubmissionRepository submissionRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final EvaluationRepository evaluationRepository;
    private final FileStorageService fileStorageService;
    private final LecturerRepository lecturerRepository;
    private final CouncilService councilService;
    private final EntityManager entityManager;

    public StudentService(
            StudentRepository studentRepository,
            TopicRepository topicRepository,
            StudentGroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            SubmissionRepository submissionRepository,
            RegistrationPeriodRepository periodRepository,
            EvaluationRepository evaluationRepository,
            FileStorageService fileStorageService,
            LecturerRepository lecturerRepository,
            CouncilService councilService,
            EntityManager entityManager) {
        this.studentRepository = studentRepository;
        this.topicRepository = topicRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.submissionRepository = submissionRepository;
        this.periodRepository = periodRepository;
        this.evaluationRepository = evaluationRepository;
        this.fileStorageService = fileStorageService;
        this.lecturerRepository = lecturerRepository;
        this.councilService = councilService;
        this.entityManager = entityManager;
    }

    Student lockStudentForMembership(Student student) {
        Student locked = studentRepository.findForMembership(student.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy sinh viên!"));
        entityManager.refresh(locked);
        return locked;
    }

    StudentGroup lockGroupForMembership(Long groupId) {
        StudentGroup locked = groupRepository.findForMembership(groupId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy nhóm!"));
        entityManager.refresh(locked);
        return locked;
    }

    /**
     * GLOBAL VALIDATION (Áp dụng cho 100% sinh viên, không ngoại lệ): 1. Mỗi
     * Đợt đăng ký (registration_period) thuộc về một Khoa cụ thể
     * (department_id). 2. Bất kỳ sinh viên nào (student) được mời vào nhóm hoặc
     * tham gia trong đợt đó, hệ thống BẮT BUỘC phải check:
     * student.department_id PHẢI TRÙNG KHỚP với period.department_id. 3. Nếu
     * khác khoa, tuyệt đối từ chối và chặn đứng ở tầng Backend, ném ra lỗi 400
     * với thông báo: "Sinh viên không thuộc khoa quản lý đợt đăng ký này!".
     */
    public void validateStudentDepartmentInPeriod(Student student, RegistrationPeriod period) {
        if (student == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy thông tin sinh viên!");
        }
        if (period == null) {
            return;
        }

        // Lấy Khoa của đợt đăng ký
        Department periodDept = period.getDepartment();
        if (periodDept == null && period.getId() != null) {
            RegistrationPeriod reloaded = periodRepository.findById(period.getId()).orElse(null);
            if (reloaded != null) {
                periodDept = reloaded.getDepartment();
            }
        }
        if (periodDept == null || periodDept.getId() == null) {
            return; // Đợt dùng chung toàn trường không gán khoa
        }

        // Lấy Khoa của sinh viên trực tiếp từ hồ sơ sinh viên
        Department studentDept = student.getDepartment();

        if (studentDept == null || !Objects.equals(studentDept.getId(), periodDept.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sinh viên không thuộc khoa quản lý đợt đăng ký này!");
        }
    }

    @Transactional(readOnly = true)
    public Student getStudentByUser(AppUser user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return studentRepository.findByUserId(user.getId()).orElse(null);
    }

    public Student getStudentByCode(String studentCode) {
        if (studentCode == null || studentCode.trim().isEmpty()) {
            return null;
        }
        return studentRepository.findByStudentCode(studentCode.trim()).orElse(null);
    }

    // ==================== GROUP MANAGEMENT (MAX 3 SV) ====================
    /**
     * BUG-H FIX: Khi periodId = null, uu tien tra ve nhom trong dot dang hoat
     * dong nhat (isActive + status OPEN/IN_PROGRESS) thay vi nhom cuoi cung
     * theo insert order.
     */
    public StudentGroup getMyActiveGroup(Student student, Long periodId) {
        if (student == null) {
            return null;
        }

        // Case 1: period cu the duoc chi dinh
        if (periodId != null) {
            List<StudentGroup> list = groupRepository.findByStudentIdAndPeriodId(student.getId(), periodId).stream()
                    .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
                    .toList();
            return list.isEmpty() ? null : list.get(0);
        }

        // Case 2: Khong co periodId — tim nhom qua 3 tang uu tien
        List<StudentGroup> allMyGroups = groupRepository.findByStudentId(student.getId());
        if (allMyGroups.isEmpty()) return null;
        

        // Tang 1: Trong cac nhom cua sinh vien, loc nhom co topic khac null VA status khac DISSOLVED.
        // Neu co it nhat 1 nhom nhu vay thi tra ve nhom tot nhat (uu tien dot active + OPEN/IN_PROGRESS truoc, sau do createdAt moi nhat)
        Optional<StudentGroup> groupWithTopic = allMyGroups.stream()
                .filter(g -> g.getTopic() != null && !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
                .max(Comparator
                        .comparing((StudentGroup g) -> {
                            RegistrationPeriod p = g.getPeriod();
                            return p != null
                                    && Boolean.TRUE.equals(p.getIsActive())
                                    && ("OPEN".equalsIgnoreCase(p.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(p.getStatus()));
                        })
                        .thenComparing(StudentGroup::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        if (groupWithTopic.isPresent()) {
            return groupWithTopic.get();
        }

        // Tang 2: Neu khong nhom nao co de tai, giu nguyen luat hien tai
        // (nhom thuoc dot isActive va OPEN/IN_PROGRESS co period id lon nhat)
        return allMyGroups.stream()
                .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
            .filter(g -> g.getPeriod() != null
                        && Boolean.TRUE.equals(g.getPeriod().getIsActive())
                        && ("OPEN".equalsIgnoreCase(g.getPeriod().getStatus())
                        || "IN_PROGRESS".equalsIgnoreCase(g.getPeriod().getStatus())))
                .max(Comparator.comparing(g -> g.getPeriod().getId()))
                // Tang 3 (Fallback): giu nguyen nhu cu (nhom co createdAt moi nhat)
                .orElseGet(() -> allMyGroups.stream()
                        .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
                        .max(Comparator.comparing(StudentGroup::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(null));
    }

    @Transactional
    public StudentGroup createGroup(Student leader, Long periodId, String groupName) {
        leader = lockStudentForMembership(leader);
        RegistrationPeriod period = periodId != null ? periodRepository.findById(periodId).orElse(null) : null;
        if (period == null) {
            List<RegistrationPeriod> activePeriods = periodRepository.findByIsActiveTrueOrderByIdDesc();
            if (!activePeriods.isEmpty()) {
                period = activePeriods.get(0);
            }
        }

        // GLOBAL VALIDATION: Kiểm tra tính đồng bộ Khoa cho Trưởng nhóm
        if (period != null) {
            validateStudentDepartmentInPeriod(leader, period);
        }

        // Check if student already in a group in this period
        if (period != null) {
            List<StudentGroup> existing = groupRepository.findByStudentIdAndPeriodId(leader.getId(), period.getId()).stream()
                    .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
                    .toList();
            if (!existing.isEmpty()) {
                throw new IllegalStateException("Bạn đã tham gia một nhóm trong đợt này rồi (" + existing.get(0).getGroupName() + ").");
            }
        }

        StudentGroup group = new StudentGroup();
        group.setPeriod(period);
        group.setLeader(leader);
        group.setGroupName((groupName != null && !groupName.trim().isEmpty()) ? groupName.trim() : "Nhóm của " + leader.getUser().getFullName());
        group.setGroupCode("GRP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        group.setStatus("DRAFT");
        group.setCreatedAt(OffsetDateTime.now());

        StudentGroup saved = groupRepository.save(group);

        // Add leader as first member
        GroupMember gm = new GroupMember();
        gm.setGroup(saved);
        gm.setStudent(leader);
        gm.setRoleInGroup("LEADER");
        gm.setJoinedAt(OffsetDateTime.now());
        groupMemberRepository.save(gm);

        System.out.println("[DB-STUDENT] Đã tạo nhóm mới: '" + saved.getGroupName() + "' (Mã: " + saved.getGroupCode() + ") bởi Leader " + leader.getStudentCode());
        return saved;
    }

    private int getMaxAllowedMembers(StudentGroup group) {
        if (group == null) {
            return 5;
        }
        if (group.getTopic() != null && group.getTopic().getMaxStudents() != null && group.getTopic().getMaxStudents() > 0) {
            return Math.min(5, Math.max(3, group.getTopic().getMaxStudents()));
        }
        if (group.getPeriod() != null && group.getPeriod().getMaxStudents() != null && group.getPeriod().getMaxStudents() > 0) {
            return Math.min(5, Math.max(3, group.getPeriod().getMaxStudents()));
        }
        return 5;
    }

    @Transactional
    public String inviteMemberByCode(StudentGroup group, String studentCode) {
        if (group == null) {
            return "Không tìm thấy nhóm.";
        }
        Student invitee = getStudentByCode(studentCode);
        if (invitee == null) {
            return "Không tìm thấy sinh viên có mã: " + studentCode;
        }
        invitee = lockStudentForMembership(invitee);
        group = lockGroupForMembership(group.getId());
        List<GroupMember> currentMembers = groupMemberRepository.findByGroupId(group.getId());

        // BUG-03 FIX: Kiểm tra giới hạn thành viên theo topic.maxStudents nếu nhóm đã có đề tài (3 - 5 SV theo Rubric)
        int maxAllowed = getMaxAllowedMembers(group);
        if (currentMembers.size() >= maxAllowed) {
            return "Nhóm đã đạt số lượng tối đa " + maxAllowed + " thành viên" + (group.getTopic() != null ? " theo quy định đề tài!" : "!");
        }

        // GLOBAL VALIDATION: Bắt buộc sinh viên được mời phải cùng Khoa với Đợt đăng ký
        RegistrationPeriod period = group.getPeriod();
        if (period != null) {
            try {
                validateStudentDepartmentInPeriod(invitee, period);
            } catch (ResponseStatusException ex) {
                return ex.getReason() != null ? ex.getReason() : "Sinh viên không thuộc khoa quản lý đợt đăng ký này!";
            }
        }

        Long periodId = period != null ? period.getId() : null;
        List<StudentGroup> existing = groupRepository.findByStudentIdAndPeriodId(invitee.getId(), periodId).stream()
                .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
                .toList();
        if (!existing.isEmpty()) {
            return "Sinh viên " + invitee.getUser().getFullName() + " (" + studentCode + ") đã tham gia một nhóm khác trong đợt này!";
        }

        GroupMember gm = new GroupMember();
        gm.setGroup(group);
        gm.setStudent(invitee);
        gm.setRoleInGroup("MEMBER");
        gm.setJoinedAt(OffsetDateTime.now());
        groupMemberRepository.save(gm);

        System.out.println("[DB-STUDENT] Đã thêm thành viên " + invitee.getStudentCode() + " vào nhóm " + group.getGroupCode());
        return null; // success
    }

    @Transactional
    public String joinGroupByCode(Student student, String groupCode) {
        if (student == null) {
            return "Vui lòng đăng nhập.";
        }
        if (groupCode == null || groupCode.trim().isEmpty()) {
            return "Vui lòng nhập mã nhóm.";
        }
        student = lockStudentForMembership(student);

        Optional<StudentGroup> opt = groupRepository.findByGroupCode(groupCode.trim().toUpperCase());
        if (opt.isEmpty()) {
            return "Mã nhóm không tồn tại!";
        }

        StudentGroup group = lockGroupForMembership(opt.get().getId());
        RegistrationPeriod period = group.getPeriod();

        // GLOBAL VALIDATION: Bắt buộc sinh viên gia nhập phải cùng Khoa với Đợt đăng ký
        if (period != null) {
            try {
                validateStudentDepartmentInPeriod(student, period);
            } catch (ResponseStatusException ex) {
                return ex.getReason() != null ? ex.getReason() : "Sinh viên không thuộc khoa quản lý đợt đăng ký này!";
            }
        }

        List<GroupMember> currentMembers = groupMemberRepository.findByGroupId(group.getId());

        // BUG-03 FIX: Kiểm tra giới hạn thành viên theo topic.maxStudents nếu nhóm đã có đề tài (3 - 5 SV theo Rubric)
        int maxAllowed = getMaxAllowedMembers(group);
        if (currentMembers.size() >= maxAllowed) {
            return "Nhóm " + group.getGroupName() + " đã đủ " + maxAllowed + " thành viên" + (group.getTopic() != null ? " theo quy định đề tài!" : "!");
        }

        Long periodId = period != null ? period.getId() : null;
        List<StudentGroup> existing = groupRepository.findByStudentIdAndPeriodId(student.getId(), periodId).stream()
                .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
                .toList();
        if (!existing.isEmpty()) {
            return "Bạn đã thuộc một nhóm khác trong đợt này rồi!";
        }

        GroupMember gm = new GroupMember();
        gm.setGroup(group);
        gm.setStudent(student);
        gm.setRoleInGroup("MEMBER");
        gm.setJoinedAt(OffsetDateTime.now());
        groupMemberRepository.save(gm);

        System.out.println("[DB-STUDENT] Sinh viên " + student.getStudentCode() + " đã gia nhập nhóm " + group.getGroupCode());
        return null; // success
    }

    @Transactional
    public boolean removeMember(StudentGroup group, Long memberStudentId) {
        if (group == null || memberStudentId == null) {
            return false;
        }
        // Cannot remove leader
        if (group.getLeader() != null && group.getLeader().getId().equals(memberStudentId)) {
            return false;
        }
        if ("APPROVED".equalsIgnoreCase(group.getStatus()) || "REGISTERED".equalsIgnoreCase(group.getStatus())) {
            System.err.println("[SECURITY] Không thể tự ý xóa thành viên khi nhóm đã ở trạng thái " + group.getStatus());
            return false;
        }
        groupMemberRepository.deleteByGroupIdAndStudentId(group.getId(), memberStudentId);
        return true;
    }

    // ==================== TOPIC REGISTRATION ====================
    /**
     * Lấy danh sách đề tài APPROVED, ưu tiên lọc theo Khoa của sinh viên
     * (departmentId). Nếu departmentId != null: chỉ trả về đề tài thuộc Khoa
     * đó. Kết hợp thêm bộ lọc periodId, keyword.
     */
    public List<Topic> getAvailableTopics(Long periodId, String keyword, Long departmentId) {
        List<Topic> list = (periodId != null)
                ? topicRepository.findApprovedByPeriodId(periodId)
                : topicRepository.findByStatus("APPROVED");
        return list.stream()
                // Lọc theo Khoa của sinh viên (nếu có departmentId)
                .filter(t -> departmentId == null
                        || (t.getDepartment() != null
                        && departmentId.equals(t.getDepartment().getId()))
                        || (t.getPeriod() != null
                        && t.getPeriod().getDepartment() != null
                        && departmentId.equals(t.getPeriod().getDepartment().getId())))
                // BUG-02 FIX: Chỉ hiển thị đề tài trong đợt đang mở đăng ký cho sinh viên
                .filter(t -> t.getPeriod() == null || t.getPeriod().isStudentRegistrationOpen())
                .filter(t -> keyword == null || keyword.trim().isEmpty()
                        || (t.getTitle() != null && t.getTitle().toLowerCase().contains(keyword.toLowerCase()))
                        || (t.getCode() != null && t.getCode().toLowerCase().contains(keyword.toLowerCase())))
                .collect(Collectors.toList());
    }

    /**
     * Trả về tập hợp các topicId đã có nhóm đăng ký (trạng thái != DRAFT, !=
     * REJECTED). Dùng để UI hiển thị trực quan đề tài nào còn trống / đã kín
     * chỗ.
     */
    public java.util.Set<Long> getTakenTopicIds(List<Topic> topics) {
        if (topics == null || topics.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        List<Long> topicIds = topics.stream().map(Topic::getId).collect(Collectors.toList());
        List<StudentGroup> groups = groupRepository.findByTopicIdIn(topicIds);
        java.util.Set<Long> takenIds = new java.util.HashSet<>();
        for (StudentGroup g : groups) {
            // Chỉ tính nhóm đang ACTIVE (không phải DRAFT hay REJECTED)
            if (g.getTopic() != null && g.getStatus() != null
                    && !"DRAFT".equalsIgnoreCase(g.getStatus())
                    && !"REJECTED".equalsIgnoreCase(g.getStatus())) {
                takenIds.add(g.getTopic().getId());
            }
        }
        return takenIds;
    }

    public Topic getTopicById(Long id) {
        if (id == null) {
            return null;
        }
        return topicRepository.findById(id).orElse(null);
    }

    @Transactional
    public String registerTopicForGroup(Student student, StudentGroup group, Long topicId) {
        if (student == null) {
            return "Vui lòng đăng nhập lại!";
        }

        Topic topic = getTopicById(topicId);
        if (topic == null || !"APPROVED".equalsIgnoreCase(topic.getStatus())) {
            return "Đề tài không tồn tại hoặc chưa được duyệt!";
        }

        if (topic.getPeriod() == null) {
            return "Đề tài chưa thuộc đợt đăng ký nào, không thể đăng ký!";
        }

        Long topicPeriodId = topic.getPeriod().getId();

        // Nếu nhóm truyền vào là null, tìm nhóm của sinh viên (ưu tiên theo đợt, fallback nhóm hiện có)
        if (group == null) {
            group = getMyActiveGroup(student, topicPeriodId);
        }

        // Tự động khởi tạo nhóm CHỈ khi sinh viên chưa có nhóm ở bất kỳ đợt nào
        if (group == null) {
            // Thay đổi 1: Kiểm tra nếu sinh viên đã có nhóm khác chưa bị DISSOLVED ở đợt khác thì không tự tạo nhóm
            List<StudentGroup> allMyGroups = groupRepository.findByStudentId(student.getId());
            final Long topicPeriodIdFinal = topicPeriodId;

            Optional<StudentGroup> otherPeriodGroup = allMyGroups.stream()
                    .filter(g -> !"DISSOLVED".equalsIgnoreCase(g.getStatus()))
                    .filter(g -> g.getPeriod() != null && !Objects.equals(g.getPeriod().getId(), topicPeriodIdFinal))
                    .max(Comparator.comparing((StudentGroup g) -> {
                                RegistrationPeriod p = g.getPeriod();
                                return p != null && Boolean.TRUE.equals(p.getIsActive())
                                        && ("OPEN".equalsIgnoreCase(p.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(p.getStatus()));
                            })
                            .thenComparing(g -> g.getTopic() != null)
                            .thenComparing(StudentGroup::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

            if (otherPeriodGroup.isPresent()) {
                StudentGroup existingGroup = otherPeriodGroup.get();
                String existingPeriodName = (existingGroup.getPeriod() != null && existingGroup.getPeriod().getName() != null)
                        ? existingGroup.getPeriod().getName()
                        : "khác";
                String topicPeriodName = (topic.getPeriod() != null && topic.getPeriod().getName() != null)
                        ? topic.getPeriod().getName()
                        : "";
                return "Nhóm của bạn đang thuộc đợt " + existingPeriodName
                        + ", không thể đăng ký đề tài thuộc đợt " + topicPeriodName
                        + ". Mỗi đợt sử dụng một nhóm riêng — vui lòng tạo nhóm trong đợt " + topicPeriodName
                        + " trước khi đăng ký!";
            }

            try {
                String studentName = (student.getUser() != null && student.getUser().getFullName() != null)
                        ? student.getUser().getFullName()
                        : student.getStudentCode();
                group = createGroup(student, topicPeriodId, "Nhóm của " + studentName);
            } catch (Exception ex) {
                return "Không thể khởi tạo nhóm tự động: " + ex.getMessage();
            }
        }
        student = lockStudentForMembership(student);
        group = lockGroupForMembership(group.getId());
        topic = topicRepository.findForPublication(topicId).orElseThrow();
        entityManager.refresh(topic);
        topicPeriodId = topic.getPeriod() != null ? topic.getPeriod().getId() : null;
        List<Long> supervisorIds = new ArrayList<>();
        if (topic.getSupervisor() != null) {
            supervisorIds.add(topic.getSupervisor().getId());
        }
        if (topic.getCoSupervisor() != null) {
            supervisorIds.add(topic.getCoSupervisor().getId());
        }
        for (Long id : supervisorIds.stream().distinct().sorted().toList()) {
            Lecturer lecturer = lecturerRepository.findForQuota(id).orElseThrow();
            entityManager.refresh(lecturer);
        }
        if (!"APPROVED".equalsIgnoreCase(topic.getStatus())) {
            return "Đề tài chưa được duyệt!";
        }

        // Kiểm tra quyền Trưởng nhóm: Chỉ Trưởng nhóm mới có quyền chọn và đăng ký đề tài
        if (!group.isLeader(student)) {
            return "Chỉ Nhóm trưởng mới có quyền chọn và đăng ký đề tài cho nhóm!";
        }

        // Thay đổi 2: Ràng buộc khớp đợt đăng ký giữa nhóm và đề tài (siết chặt null)
        if (group.getPeriod() == null || !Objects.equals(group.getPeriod().getId(), topic.getPeriod().getId())) {
            String groupPeriodName = (group.getPeriod() != null && group.getPeriod().getName() != null)
                    ? "'" + group.getPeriod().getName() + "'"
                    : "'chưa xác định'";
            return "Nhóm sinh viên thuộc đợt " + groupPeriodName
                    + ", không thể đăng ký đề tài của đợt '" + topic.getPeriod().getName() + "'!";
        }

        // GLOBAL VALIDATION: Chặn tuyệt đối đề tài khác Khoa
        Department studentDept = student.getDepartment();
        Department topicDept = topic.getDepartment();
        if (studentDept != null && topicDept != null && !Objects.equals(studentDept.getId(), topicDept.getId())) {
            return "Sinh viên " + studentDept.getName() + " không được đăng ký đề tài thuộc " + topicDept.getName() + "!";
        }

        // Kiểm tra nhóm đã đăng ký đề tài nào khác trong đợt này chưa
        if (group.getTopic() != null) {
            return "Nhóm của bạn đã đăng ký đề tài: '" + group.getTopic().getTitle() + "'. Mỗi nhóm chỉ được đăng ký 1 đề tài trong một đợt!";
        }

        // Kiểm tra thời hạn đợt đăng ký
        if (topic.getPeriod() != null && !topic.getPeriod().isStudentRegistrationOpen()) {
            return "Đợt đăng ký đề tài này hiện không trong thời gian mở đăng ký cho sinh viên!";
        }

        // Thay đổi 3: Ràng buộc số lượng thành viên nhóm tối thiểu 2 SV, tối đa theo quy định đề tài
        List<GroupMember> currentMembers = groupMemberRepository.findByGroupId(group.getId());
        int memberCount = (currentMembers != null && !currentMembers.isEmpty()) ? currentMembers.size() : 1;
        int minStudentsAllowed = 2;
        if (memberCount < minStudentsAllowed) {
            return "Nhóm cần tối thiểu " + minStudentsAllowed + " thành viên mới được đăng ký đề tài (hiện có " + memberCount + "). Vui lòng vào mục 'Quản lý nhóm' để mời thêm thành viên trước khi đăng ký!";
        }
        int maxStudentsAllowed = (topic.getMaxStudents() != null && topic.getMaxStudents() > 0)
                ? Math.min(5, Math.max(minStudentsAllowed, topic.getMaxStudents()))
                : 5;

        if (memberCount > maxStudentsAllowed) {
            return "Số lượng thành viên của nhóm (" + memberCount + " SV) vượt quá số lượng tối đa cho phép của đề tài này (" + maxStudentsAllowed + " SV)!";
        }

        // Kiểm tra chỉ tiêu đăng ký (mỗi đề tài chỉ 1 nhóm thực hiện)
        List<StudentGroup> existingGroupsOnTopic = groupRepository.findByTopicId(topicId);
        if (!existingGroupsOnTopic.isEmpty()) {
            return "Đề tài này đã được một nhóm khác đăng ký rồi!";
        }

        // BUG-B FIX: Kiểm tra hạn mức hướng dẫn (maxTopicsQuota) của GVHD chính
        // Quota tính theo đợt: đếm số nhóm REGISTERED/APPROVED mà GVHD đang nhận trong đợt này
        List<String> excludedStatuses = List.of("DRAFT", "REJECTED");

        if (topic.getSupervisor() != null) {
            Integer quota = topic.getSupervisor().getMaxTopicsQuota();
            if (quota != null && quota > 0) {
                long currentCount = groupRepository.countActiveGroupsBySupervisorInPeriod(
                        topic.getSupervisor().getId(), topicPeriodId, excludedStatuses);
                if (currentCount >= quota) {
                    String supervisorName = topic.getSupervisor().getUser() != null
                            ? topic.getSupervisor().getUser().getFullName()
                            : "GV#" + topic.getSupervisor().getId();
                    return "Giảng viên hướng dẫn '" + supervisorName
                            + "' đã đạt hạn mức hướng dẫn tối đa (" + quota
                            + " nhóm/đợt). Vui lòng chọn đề tài khác!";
                }
            }
        }

        // BUG-B FIX: Kiểm tra hạn mức của GVHD phụ (nếu có)
        if (topic.getCoSupervisor() != null) {
            Integer coQuota = topic.getCoSupervisor().getMaxTopicsQuota();
            if (coQuota != null && coQuota > 0) {
                long coCount = groupRepository.countActiveGroupsByCoSupervisorInPeriod(
                        topic.getCoSupervisor().getId(), topicPeriodId, excludedStatuses);
                if (coCount >= coQuota) {
                    String coName = topic.getCoSupervisor().getUser() != null
                            ? topic.getCoSupervisor().getUser().getFullName()
                            : "GV#" + topic.getCoSupervisor().getId();
                    return "Giảng viên đồng hướng dẫn '" + coName
                            + "' đã đạt hạn mức hướng dẫn tối đa (" + coQuota
                            + " nhóm/đợt). Vui lòng chọn đề tài khác!";
                }
            }
        }

        try {
            group.setTopic(topic);
            group.setStatus("REGISTERED");
            groupRepository.save(group);
            System.out.println("[DB-STUDENT] Nhom '" + group.getGroupName() + "' da dang ky de tai '" + topic.getTitle() + "'");
            return null; // success
        } catch (OptimisticLockingFailureException e) {
            System.err.println("[CONCURRENCY] Tranh chấp đồng thời khi đăng ký đề tài ID=" + topicId + ": " + e.getMessage());
            return "Đề tài vừa được một nhóm khác đăng ký trước đó. Vui lòng làm mới trang và chọn đề tài khác!";
        }
    }

    // ==================== SUBMISSIONS (LEADER ONLY) ====================
    public List<Submission> getGroupSubmissions(StudentGroup group) {
        if (group == null) {
            return Collections.emptyList();
        }
        List<Submission> list = submissionRepository.findByGroupId(group.getId());
        return list.stream()
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional
    public String submitGroupReport(Student student, StudentGroup group, String title, Integer round, org.springframework.web.multipart.MultipartFile file) {
        if (group == null) {
            return "Bạn chưa thuộc nhóm nào!";
        }

        if (group.getId() != null) {
            group = groupRepository.findById(group.getId()).orElse(group);
        }

        // BẮT BUỘC: CHỈ NHÓM TRƯỞNG MỚI ĐƯỢC NỘP BÁO CÁO ĐỀ TÀI
        if (!group.isLeader(student)) {
            return "QUYỀN HẠN BỊ TỪ CHỐI: Chỉ Nhóm trưởng mới có quyền nộp bài báo cáo đề tài!";
        }

        if (group.getTopic() == null) {
            return "Nhóm của bạn chưa đăng ký đề tài nào!";
        }

        // Chỉ cho phép nộp báo cáo khi đề tài và đăng ký nhóm đã được GVHD phê duyệt (APPROVED)
        if (!"APPROVED".equalsIgnoreCase(group.getStatus()) || !"APPROVED".equalsIgnoreCase(group.getTopic().getStatus())) {
            return "Đề tài chưa được phê duyệt, không thể nộp báo cáo!";
        }

        if (file == null || file.isEmpty()) {
            return "Vui lòng chọn file báo cáo cần nộp (PDF, Word, ZIP, PPTX)!";
        }

        try {
            FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file);

            Submission sub = new Submission();
            sub.setTopic(group.getTopic());
            sub.setGroup(group);
            sub.setSubmittedByStudent(student);
            sub.setTitle(title != null && !title.isBlank() ? title.trim() : ("Báo cáo đợt " + (round != null ? round : 1)));
            sub.setSubmissionRound(round != null ? round : 1);
            sub.setFilePath(storedFile.getFilePath());
            sub.setOriginalFileName(storedFile.getOriginalFileName());
            sub.setContentType(storedFile.getContentType());
            sub.setFileSize(storedFile.getFileSize());
            sub.setSubmittedAt(OffsetDateTime.now());

            submissionRepository.save(sub);
            System.out.println("[DB-STUDENT] Leader " + student.getStudentCode() + " đã nộp file báo cáo thực tế '" + storedFile.getOriginalFileName() + "' cho nhóm " + group.getGroupName());
            return null; // success
        } catch (Exception e) {
            return "Lỗi nộp file: " + e.getMessage();
        }
    }

    // ==================== RESULTS & EVALUATIONS ====================
    /**
     * BẢO MẬT & PHÂN QUYỀN TRA CỨU ĐIỂM (Rubric Mục 4 & Báo cáo Điểm 2): Củng
     * cố bảo mật tại tầng Service cho mọi luồng gọi: 1. Xác minh người đang xem
     * (viewer) là sinh viên hợp lệ. 2. Xác minh sinh viên phải là thành viên
     * chính thức thuộc nhóm (Group). 3. Xác minh Đợt (Period): Sinh viên chỉ
     * được tra cứu điểm của Đợt mà nhóm mình tham gia. 4. Xác minh trạng thái
     * công bố: Đề tài BẮT BUỘC phải có isFinalized = true.
     */
    public List<Evaluation> getGroupEvaluations(StudentGroup group, Student viewer, Long periodId) {
        if (group == null || group.getId() == null || viewer == null || viewer.getId() == null) {
            return Collections.emptyList();
        }

        StudentGroup freshGroup = groupRepository.findById(group.getId()).orElse(group);
        if (freshGroup.getTopic() == null || freshGroup.getTopic().getId() == null) {
            return Collections.emptyList();
        }

        Topic freshTopic = topicRepository.findById(freshGroup.getTopic().getId()).orElse(freshGroup.getTopic());
        // Guard 1: Đề tài BẮT BUỘC phải được công bố chính thức mới lộ phiếu đánh giá
        if (!Boolean.TRUE.equals(freshTopic.getIsFinalized())) {
            return Collections.emptyList();
        }

        // Guard 2: Sinh viên xem BẮT BUỘC phải là thành viên (Leader hoặc Member) của chính nhóm này
        boolean isMember = freshGroup.isLeader(viewer)
                || groupMemberRepository.findByGroupIdAndStudentId(freshGroup.getId(), viewer.getId()).isPresent();
        if (!isMember) {
            return Collections.emptyList();
        }

        // Guard 3: Kiểm tra Đợt (Period) - Sinh viên chỉ được xem điểm của đợt mình đã tham gia
        if (freshGroup.getPeriod() != null) {
            Long groupPeriodId = freshGroup.getPeriod().getId();
            if (periodId != null && !Objects.equals(groupPeriodId, periodId)) {
                return Collections.emptyList();
            }
            List<RegistrationPeriod> participatedPeriods = getParticipatedPeriods(viewer);
            boolean periodMatch = participatedPeriods.stream()
                    .anyMatch(p -> p.getId() != null && p.getId().equals(groupPeriodId));
            if (!periodMatch) {
                return Collections.emptyList();
            }
        }

        return councilService.getEvaluationsForTopic(freshTopic.getId());
    }

    public List<Evaluation> getGroupEvaluations(StudentGroup group, Student viewer) {
        return getGroupEvaluations(group, viewer, null);
    }

    public List<Evaluation> getGroupEvaluations(StudentGroup group) {
        return getGroupEvaluations(group, null, null);
    }

    public List<RegistrationPeriod> getParticipatedPeriods(Student student) {
        if (student == null || student.getId() == null) {
            return Collections.emptyList();
        }
        List<StudentGroup> groups = groupRepository.findByStudentId(student.getId());
        return groups.stream()
                .map(StudentGroup::getPeriod)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(RegistrationPeriod::getId).reversed())
                .collect(Collectors.toList());
    }

    @Cacheable("activePeriods")
    public List<RegistrationPeriod> getAllActivePeriods() {
        return periodRepository.findByIsActiveTrueOrderByIdDesc();
    }

    public List<RegistrationPeriod> getActivePeriodsForStudent(Student student) {
        if (student != null && student.getDepartmentId() != null) {
            return periodRepository.findByDepartmentId(student.getDepartmentId());
        }
        return getAllActivePeriods();
    }

    /**
     * Kiểm tra quyền xem điểm tổng kết đề tài: - ADMIN: được xem -
     * DEPARTMENT_HEAD: được xem trong khoa quản lý - LECTURER: được xem nếu là
     * GVHD, GVPB hoặc thành viên Hội đồng đề tài - STUDENT: CHỈ ĐƯỢC XEM KHI ĐÃ
     * CÔNG BỐ CHÍNH THỨC (isFinalized = true) VÀ LÀ THÀNH VIÊN NHÓM
     */
    public boolean canUserViewTopicScore(AppUser user, Topic topic, StudentGroup group) {
        if (user == null || topic == null) {
            return false;
        }
        String role = user.getRole() != null ? user.getRole().getCode() : "";
        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(role)) {
            Lecturer manager = lecturerRepository.findByUserId(user.getId()).orElse(null);
            return manager != null && manager.getDepartment() != null && topic.getDepartment() != null
                    && Objects.equals(topic.getDepartment().getId(), manager.getDepartment().getId());
        }
        if ("LECTURER".equalsIgnoreCase(role)) {
            if (topic.getSupervisor() != null && topic.getSupervisor().getUser() != null
                    && Objects.equals(topic.getSupervisor().getUser().getId(), user.getId())) {
                return true;
            }
            if (topic.getCoSupervisor() != null && topic.getCoSupervisor().getUser() != null
                    && Objects.equals(topic.getCoSupervisor().getUser().getId(), user.getId())) {
                return true;
            }
            if (topic.getReviewer() != null && topic.getReviewer().getUser() != null
                    && Objects.equals(topic.getReviewer().getUser().getId(), user.getId())) {
                return true;
            }
            if (topic.getCouncil() != null && topic.getCouncil().getMembers() != null) {
                boolean inCouncil = topic.getCouncil().getMembers().stream()
                        .anyMatch(m -> m.getLecturer() != null && m.getLecturer().getUser() != null
                                && Objects.equals(m.getLecturer().getUser().getId(), user.getId()));
                if (inCouncil) {
                    return true;
                }
            }
            return false;
        }
        if ("STUDENT".equalsIgnoreCase(role)) {
            // Chặn đứng IDOR: Đề tài chưa công bố hoặc xem đề tài nhóm khác
            if (!Boolean.TRUE.equals(topic.getIsFinalized())) {
                return false;
            }
            if (group == null) {
                return false;
            }
            Student student = studentRepository.findByUserId(user.getId()).orElse(null);
            if (student == null) {
                return false;
            }
            return group.isLeader(student)
                    || groupMemberRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).isPresent();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTopicDetailMap(Long id, AppUser currentUser) {
        Topic t = topicRepository.findById(id).orElse(null);
        if (t == null) {
            return null;
        }

        List<StudentGroup> groups = groupRepository.findByTopicId(t.getId());
        boolean hasGroup = !groups.isEmpty();
        StudentGroup group = hasGroup ? groups.get(0) : null;

        Map<String, Object> data = new HashMap<>();
        data.put("id", t.getId());
        data.put("code", t.getCode() != null ? t.getCode() : "DT-" + t.getId());
        data.put("title", t.getTitle());
        data.put("period", t.getPeriod() != null ? t.getPeriod().getName() : "Chung");
        data.put("departmentName", t.getDepartment() != null ? t.getDepartment().getName() : "Khoa");
        data.put("supervisor", t.getSupervisor() != null && t.getSupervisor().getUser() != null
                ? t.getSupervisor().getUser().getFullName()
                : "Chưa phân công");
        data.put("coSupervisor", t.getCoSupervisor() != null && t.getCoSupervisor().getUser() != null
                ? t.getCoSupervisor().getUser().getFullName()
                : "Không có");
        data.put("reviewer", t.getReviewer() != null && t.getReviewer().getUser() != null
                ? t.getReviewer().getUser().getFullName()
                : "Chưa phân công");
        data.put("council", t.getCouncil() != null ? t.getCouncil().getName() : "Chưa phân hội đồng");
        data.put("maxStudents", t.getMaxStudents() != null ? t.getMaxStudents() : 3);
        data.put("status", t.getStatus() != null ? t.getStatus() : "PENDING");
        data.put("description", t.getDescription() != null ? t.getDescription() : "Chưa có mô tả chi tiết.");
        data.put("requirements", t.getRequirements() != null ? t.getRequirements() : "Theo hướng dẫn của Giảng viên.");
        data.put("hasGroup", hasGroup);
        data.put("groupName", group != null ? group.getGroupName() : null);
        data.put("leaderName", group != null && group.getLeader() != null && group.getLeader().getUser() != null ? group.getLeader().getUser().getFullName() : null);
        data.put("leaderCode", group != null && group.getLeader() != null ? group.getLeader().getStudentCode() : null);

        // BẢO VỆ ĐIỂM SỐ: Tuyệt đối không để lộ finalScore và isFinalized cho người không có thẩm quyền hoặc sinh viên nhóm khác/chưa công bố
        boolean canViewScore = canUserViewTopicScore(currentUser, t, group);
        if (canViewScore) {
            data.put("finalScore", t.getFinalScore());
            data.put("isFinalized", t.getIsFinalized());
        }

        // Rejection reason chỉ hiển thị cho GVHD hoặc Trưởng khoa/Admin
        boolean canViewRejection = currentUser != null && currentUser.getRole() != null && ("ADMIN".equalsIgnoreCase(currentUser.getRole().getCode())
                || "DEPARTMENT_HEAD".equalsIgnoreCase(currentUser.getRole().getCode())
                || (t.getSupervisor() != null && t.getSupervisor().getUser() != null && Objects.equals(t.getSupervisor().getUser().getId(), currentUser.getId())));
        if (canViewRejection) {
            data.put("rejectionReason", t.getRejectionReason());
        }

        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTopicDetailMap(Long id) {
        return getTopicDetailMap(id, null);
    }
}
