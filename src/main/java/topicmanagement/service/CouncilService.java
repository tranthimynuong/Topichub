package topicmanagement.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import topicmanagement.entity.*;
import topicmanagement.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CouncilService {

    private final CouncilRepository councilRepository;
    private final CouncilMemberRepository councilMemberRepository;
    private final EvaluationRepository evaluationRepository;
    private final TopicRepository topicRepository;
    private final LecturerRepository lecturerRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final DepartmentRepository departmentRepository;
    private final StudentGroupRepository groupRepository;
    private final EmailService emailService;
    private final TransactionTemplate publicationTransaction;
    private final EntityManager entityManager;

    public CouncilService(
            CouncilRepository councilRepository,
            CouncilMemberRepository councilMemberRepository,
            EvaluationRepository evaluationRepository,
            TopicRepository topicRepository,
            LecturerRepository lecturerRepository,
            RegistrationPeriodRepository periodRepository,
            DepartmentRepository departmentRepository,
            StudentGroupRepository groupRepository,
            EmailService emailService,
            PlatformTransactionManager transactionManager,
            EntityManager entityManager) {
        this.councilRepository = councilRepository;
        this.councilMemberRepository = councilMemberRepository;
        this.evaluationRepository = evaluationRepository;
        this.topicRepository = topicRepository;
        this.lecturerRepository = lecturerRepository;
        this.periodRepository = periodRepository;
        this.departmentRepository = departmentRepository;
        this.groupRepository = groupRepository;
        this.emailService = emailService;
        this.publicationTransaction = new TransactionTemplate(transactionManager);
        this.publicationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.entityManager = entityManager;
    }

    public List<Council> getAllCouncils() {
        return councilRepository.findAll();
    }

    public List<Council> getCouncilsByDepartment(Long departmentId) {
        if (departmentId == null) return councilRepository.findAll();
        return councilRepository.findByDepartmentId(departmentId);
    }

    public List<Council> getCouncilsByPeriod(Long periodId) {
        if (periodId == null) return councilRepository.findAll();
        return councilRepository.findByPeriodId(periodId);
    }

    public List<Council> getCouncilsByLecturer(Long lecturerId) {
        if (lecturerId == null) return Collections.emptyList();
        return councilRepository.findByLecturerId(lecturerId);
    }

    public Council getCouncilById(Long id) {
        if (id == null) return null;
        return councilRepository.findById(id).orElse(null);
    }

    /**
     * Tạo Hội đồng mới với Transactional & Unique Name Check theo đợt
     * BUG-F FIX: Validate số lượng thành viên (3-5) tại Service layer.
     * BUG-G FIX: Bắt buộc phải có đúng 1 CHỦ TỊCH và 1 THƯ KÝ.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DIVISION_HEAD')")
    public Council createCouncil(
            AppUser actor,
            Long departmentId,
            Long periodId,
            String councilCode,
            String councilName,
            String location,
            OffsetDateTime defenseDate,
            List<Long> lecturerIds,
            List<String> roles) {
        if (councilName == null || councilName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên hội đồng không được để trống!");
        }
        String trimmedName = councilName.trim();

        // BUG-F FIX: Kiểm tra số lượng thành viên
        if (lecturerIds == null || lecturerIds.size() < 3) {
            throw new IllegalArgumentException("Hội đồng phải có ít nhất 3 thành viên (Chủ tịch, Thư ký và ít nhất 1 Ủy viên)!");
        }
        if (lecturerIds.size() > 5) {
            throw new IllegalArgumentException("Hội đồng không được vượt quá 5 thành viên!");
        }

        // BUG-G FIX: Kiểm tra bắt buộc phải có PRESIDENT và SECRETARY
        if (roles == null || roles.size() != lecturerIds.size()) {
            throw new IllegalArgumentException("Danh sách vai trò không hợp lệ!");
        }
        long presidentCount = roles.stream().filter(r -> "PRESIDENT".equalsIgnoreCase(r)).count();
        long secretaryCount = roles.stream().filter(r -> "SECRETARY".equalsIgnoreCase(r)).count();
        if (presidentCount != 1) {
            throw new IllegalArgumentException("Hội đồng phải có đúng 1 Chủ tịch Hội đồng!");
        }
        if (secretaryCount != 1) {
            throw new IllegalArgumentException("Hội đồng phải có đúng 1 Thư ký!");
        }

        if (councilRepository.existsByPeriodAndNameIgnoreCase(periodId, trimmedName)) {
            throw new IllegalArgumentException("Hội đồng với tên '" + trimmedName + "' đã tồn tại trong đợt này. Vui lòng chọn tên khác!");
        }

        String trimmedCode = councilCode != null && !councilCode.trim().isEmpty() ? councilCode.trim() : null;
        if (trimmedCode != null) {
            if (councilRepository.findByCode(trimmedCode).isPresent()) {
                throw new IllegalArgumentException("Mã hội đồng '" + trimmedCode + "' đã tồn tại. Vui lòng chọn mã khác!");
            }
        }

        if (lecturerIds != null && !lecturerIds.isEmpty()) {
            Set<Long> uniqueLecturers = new HashSet<>();
            for (Long lId : lecturerIds) {
                if (lId != null && !uniqueLecturers.add(lId)) {
                    throw new IllegalArgumentException("Không được chọn trùng giảng viên trong cùng một hội đồng!");
                }
            }
        }

        Department dept = departmentId != null ? departmentRepository.findById(departmentId).orElse(null) : null;
        RegistrationPeriod period = periodId != null ? periodRepository.findById(periodId).orElse(null) : null;
        validateCouncilScope(actor, dept, period);
        List<Lecturer> validatedLecturers = validateCouncilLecturers(dept, lecturerIds, roles, List.of());

        Council council = new Council();
        council.setName(trimmedName);
        council.setCode(trimmedCode != null ? trimmedCode : ("HD-" + System.currentTimeMillis() % 10000));
        council.setDepartment(dept);
        council.setPeriod(period);
        council.setLocation(location != null ? location.trim() : null);
        council.setDefenseDate(defenseDate != null ? defenseDate : OffsetDateTime.now().plusDays(15));
        council.setStatus("ESTABLISHED");

        Council savedCouncil = councilRepository.save(council);

        if (lecturerIds != null && roles != null) {
            for (int i = 0; i < lecturerIds.size(); i++) {
                Long lecturerId = lecturerIds.get(i);
                String role = (i < roles.size()) ? roles.get(i) : "MEMBER";

                Lecturer lecturer = validatedLecturers.get(i);
                if (lecturer != null) {
                    CouncilMember member = new CouncilMember();
                    member.setCouncil(savedCouncil);
                    member.setLecturer(lecturer);
                    member.setCouncilRole(role);
                    councilMemberRepository.save(member);
                }
            }
        }

        System.out.println("[DB-COUNCIL] Tạo mới Hội đồng: " + trimmedName + " (Mã: " + trimmedCode + ")");
        return savedCouncil;
    }

    /**
     * Tạo Hội đồng mới và đồng thời phân công danh sách đề tài/nhóm vào hội đồng (Luồng Wizard 2 bước)
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DIVISION_HEAD')")
    public Council createCouncil(
            AppUser actor,
            Long departmentId,
            Long periodId,
            String councilCode,
            String councilName,
            String location,
            OffsetDateTime defenseDate,
            List<Long> lecturerIds,
            List<String> roles,
            List<Long> topicIds) {

        Council savedCouncil = createCouncil(actor, departmentId, periodId, councilCode, councilName, location, defenseDate, lecturerIds, roles);
        if (topicIds != null && !topicIds.isEmpty()) {
            assignTopicsToCouncil(actor, savedCouncil.getId(), topicIds);
        }
        return savedCouncil;
    }

    /**
     * Phân công hàng loạt đề tài vào Hội đồng có sẵn (Batch assign)
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DIVISION_HEAD')")
    public int assignTopicsToCouncil(AppUser actor, Long councilId, List<Long> topicIds) {
        if (councilId == null || topicIds == null || topicIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long topicId : new LinkedHashSet<>(topicIds)) {
            if (topicId == null || !assignTopicToCouncil(actor, topicId, councilId)) {
                throw new IllegalArgumentException("Không thể phân công: kiểm tra khoa, đợt, thành viên hội đồng và xung đột GVHD. Toàn bộ lựa chọn đã được hủy.");
            }
            count++;
        }
        return count;
    }

    /**
     * Cập nhật thông tin Hội đồng & Danh sách thành viên (có kiểm tra trùng lặp)
     * BUG-F FIX: Validate số lượng thành viên (3-5) tại Service layer.
     * BUG-G FIX: Bắt buộc phải có đúng 1 CHỦ TỊCH và 1 THƯ KÝ.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DIVISION_HEAD')")
    public Council updateCouncil(
            AppUser actor,
            Long councilId,
            Long periodId,
            String councilCode,
            String councilName,
            String location,
            OffsetDateTime defenseDate,
            List<Long> lecturerIds,
            List<String> roles) {

        if (councilId == null) {
            throw new IllegalArgumentException("ID hội đồng không hợp lệ!");
        }
        Council council = councilRepository.findForUpdate(councilId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hội đồng với ID=" + councilId));

        if (councilName == null || councilName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên hội đồng không được để trống!");
        }
        String trimmedName = councilName.trim();

        // BUG-F FIX: Kiểm tra số lượng thành viên khi cập nhật
        if (lecturerIds != null) {
            if (lecturerIds.size() < 3) {
                throw new IllegalArgumentException("Hội đồng phải có ít nhất 3 thành viên!");
            }
            if (lecturerIds.size() > 5) {
                throw new IllegalArgumentException("Hội đồng không được vượt quá 5 thành viên!");
            }
        }

        // BUG-G FIX: Kiểm tra bắt buộc PRESIDENT và SECRETARY khi cập nhật
        if (roles != null && lecturerIds != null && roles.size() == lecturerIds.size()) {
            long presidentCount = roles.stream().filter(r -> "PRESIDENT".equalsIgnoreCase(r)).count();
            long secretaryCount = roles.stream().filter(r -> "SECRETARY".equalsIgnoreCase(r)).count();
            if (presidentCount != 1) {
                throw new IllegalArgumentException("Hội đồng phải có đúng 1 Chủ tịch Hội đồng!");
            }
            if (secretaryCount != 1) {
                throw new IllegalArgumentException("Hội đồng phải có đúng 1 Thư ký!");
            }
        }

        if (councilRepository.existsByPeriodAndNameIgnoreCaseExcludingId(periodId, trimmedName, councilId)) {
            throw new IllegalArgumentException("Hội đồng với tên '" + trimmedName + "' đã tồn tại trong đợt này. Vui lòng chọn tên khác!");
        }

        String trimmedCode = councilCode != null && !councilCode.trim().isEmpty() ? councilCode.trim() : null;
        if (trimmedCode != null) {
            Optional<Council> existingWithCode = councilRepository.findByCode(trimmedCode);
            if (existingWithCode.isPresent() && !existingWithCode.get().getId().equals(councilId)) {
                throw new IllegalArgumentException("Mã hội đồng '" + trimmedCode + "' đã tồn tại ở hội đồng khác. Vui lòng chọn mã khác!");
            }
        }

        if (lecturerIds != null && !lecturerIds.isEmpty()) {
            Set<Long> uniqueLecturers = new HashSet<>();
            for (Long lId : lecturerIds) {
                if (lId != null && !uniqueLecturers.add(lId)) {
                    throw new IllegalArgumentException("Không được chọn trùng giảng viên trong cùng một hội đồng!");
                }
            }
        }

        RegistrationPeriod period = periodId != null ? periodRepository.findById(periodId).orElse(null) : null;
        validateCouncilScope(actor, council.getDepartment(), period);
        List<Topic> assignedTopics = topicRepository.findByCouncilId(councilId);
        if (assignedTopics.stream().anyMatch(t -> Boolean.TRUE.equals(t.getIsFinalized()))) {
            throw new IllegalArgumentException("Cần thu hồi công bố điểm trước khi sửa hội đồng có đề tài đã công bố!");
        }
        if (assignedTopics.stream().anyMatch(t -> t.getPeriod() == null
                || !Objects.equals(t.getPeriod().getId(), period.getId()))) {
            throw new IllegalArgumentException("Không được đổi đợt của hội đồng đang có đề tài thuộc đợt khác!");
        }
        List<CouncilMember> currentMembers = councilMemberRepository.findByCouncilId(councilId);
        if (lecturerIds == null && roles == null) {
            lecturerIds = currentMembers.stream().map(m -> m.getLecturer().getId()).toList();
            roles = currentMembers.stream().map(CouncilMember::getCouncilRole).toList();
        }
        List<Lecturer> validatedLecturers = validateCouncilLecturers(council.getDepartment(), lecturerIds, roles, assignedTopics);
        council.setPeriod(period);
        council.setName(trimmedName);
        if (trimmedCode != null) {
            council.setCode(trimmedCode);
        }
        council.setLocation(location != null ? location.trim() : null);
        council.setDefenseDate(defenseDate != null ? defenseDate : council.getDefenseDate());

        Council savedCouncil = councilRepository.save(council);

        if (lecturerIds != null && roles != null) {
            List<CouncilMember> existingMembers = councilMemberRepository.findByCouncilId(councilId);
            councilMemberRepository.deleteAll(existingMembers);

            for (int i = 0; i < lecturerIds.size(); i++) {
                Long lecturerId = lecturerIds.get(i);
                String role = (i < roles.size()) ? roles.get(i) : "MEMBER";
                Lecturer lecturer = validatedLecturers.get(i);
                if (lecturer != null) {
                    CouncilMember member = new CouncilMember();
                    member.setCouncil(savedCouncil);
                    member.setLecturer(lecturer);
                    member.setCouncilRole(role);
                    councilMemberRepository.save(member);
                }
            }
        }

        System.out.println("[DB-COUNCIL] Cập nhật Hội đồng ID=" + councilId + ": " + trimmedName);
        for (Topic assigned : assignedTopics.stream().sorted(Comparator.comparing(Topic::getId)).toList()) {
            Topic locked = topicRepository.findForPublication(assigned.getId()).orElseThrow();
            entityManager.refresh(locked);
            if (Boolean.TRUE.equals(locked.getIsFinalized())) {
                throw new IllegalArgumentException("Cần thu hồi công bố điểm trước khi sửa hội đồng có đề tài đã công bố!");
            }
            calculateAndSetFinalScore(locked);
        }
        return savedCouncil;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DIVISION_HEAD')")
    public boolean assignTopicToCouncil(AppUser actor, Long topicId, Long councilId) {
        Council council = councilId != null ? councilRepository.findForUpdate(councilId).orElse(null) : null;
        Topic topic = topicRepository.findForPublication(topicId).orElse(null);
        if (topic != null) entityManager.refresh(topic);

        if (topic == null || !canManageScope(actor, topic.getDepartment())) return false;
        if (Boolean.TRUE.equals(topic.getIsFinalized())) return Objects.equals(
                topic.getCouncil() != null ? topic.getCouncil().getId() : null, councilId);
        if (councilId != null && council == null) return false;

        if (council != null) {
            if (!canManageScope(actor, council.getDepartment())
                    || topic.getDepartment() == null || council.getDepartment() == null
                    || !Objects.equals(topic.getDepartment().getId(), council.getDepartment().getId())
                    || topic.getPeriod() == null || council.getPeriod() == null
                    || !Objects.equals(topic.getPeriod().getId(), council.getPeriod().getId())) return false;
            List<CouncilMember> members = councilMemberRepository.findByCouncilId(council.getId());
            try {
                validateCouncilScope(actor, council.getDepartment(), council.getPeriod());
                validateCouncilLecturers(council.getDepartment(),
                        members.stream().map(m -> m.getLecturer() != null ? m.getLecturer().getId() : null).toList(),
                        members.stream().map(CouncilMember::getCouncilRole).toList(), List.of(topic));
            } catch (IllegalArgumentException ex) {
                return false;
            }
            for (CouncilMember cm : members) {
                if (cm.getLecturer() != null) {
                    if (topic.getSupervisor() != null && cm.getLecturer().getId().equals(topic.getSupervisor().getId())) {
                        System.err.println("[DB-COUNCIL] Xung đột lợi ích! GVHD (" + (topic.getSupervisor().getUser() != null ? topic.getSupervisor().getUser().getFullName() : "") + ") không được ngồi trong Hội đồng chấm đề tài này!");
                        return false;
                    }
                    if (topic.getCoSupervisor() != null && cm.getLecturer().getId().equals(topic.getCoSupervisor().getId())) {
                        System.err.println("[DB-COUNCIL] Xung đột lợi ích! GVHD phụ (" + (topic.getCoSupervisor().getUser() != null ? topic.getCoSupervisor().getUser().getFullName() : "") + ") không được ngồi trong Hội đồng chấm đề tài này!");
                        return false;
                    }
                }
            }
        }

        topic.setCouncil(council);
        topicRepository.save(topic);
        calculateAndSetFinalScore(topic);
        return true;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DIVISION_HEAD')")
    public boolean assignReviewer(AppUser actor, Long topicId, Long reviewerId) {
        Topic topic = topicRepository.findForPublication(topicId).orElse(null);
        if (topic != null) entityManager.refresh(topic);
        Lecturer reviewer = reviewerId != null ? lecturerRepository.findById(reviewerId).orElse(null) : null;

        if (topic == null || !canManageScope(actor, topic.getDepartment())) return false;
        if (Boolean.TRUE.equals(topic.getIsFinalized())) return Objects.equals(
                topic.getReviewer() != null ? topic.getReviewer().getId() : null, reviewerId);
        if (reviewerId != null && reviewer == null) return false;

        if (reviewer != null) {
            if (topic.getDepartment() == null || reviewer.getDepartment() == null
                    || !Objects.equals(topic.getDepartment().getId(), reviewer.getDepartment().getId())) return false;
            if (topic.getSupervisor() != null && reviewer.getId().equals(topic.getSupervisor().getId())) {
                System.err.println("[DB-COUNCIL] Xung đột lợi ích! Giảng viên phản biện không được trùng với Giảng viên hướng dẫn chính!");
                return false;
            }
            if (topic.getCoSupervisor() != null && reviewer.getId().equals(topic.getCoSupervisor().getId())) {
                System.err.println("[DB-COUNCIL] Xung đột lợi ích! Giảng viên phản biện không được trùng với Giảng viên đồng hướng dẫn!");
                return false;
            }
        }

        topic.setReviewer(reviewer);
        topicRepository.save(topic);
        calculateAndSetFinalScore(topic);
        return true;
    }

    private boolean canManageScope(AppUser actor, Department department) {
        if (actor == null || actor.getId() == null || !Boolean.TRUE.equals(actor.getIsActive()) || actor.getRole() == null) return false;
        String role = actor.getRole().getCode();
        if ("ADMIN".equalsIgnoreCase(role)) return true;
        if (!"DEPARTMENT_HEAD".equalsIgnoreCase(role)) return false;
        Lecturer manager = lecturerRepository.findByUserId(actor.getId()).orElse(null);
        if (manager == null || manager.getDepartment() == null || department == null
                || !Objects.equals(manager.getDepartment().getId(), department.getId())) return false;
        return true;
    }

    private void validateCouncilScope(AppUser actor, Department department, RegistrationPeriod period) {
        if (!canManageScope(actor, department) || department == null || period == null
                || (period.getDepartment() != null && !Objects.equals(period.getDepartment().getId(), department.getId()))) {
            throw new IllegalArgumentException("Hội đồng phải thuộc đúng khoa/bộ môn quản lý và một đợt hợp lệ cùng khoa!");
        }
    }

    private List<Lecturer> validateCouncilLecturers(Department department, List<Long> ids, List<String> roles, List<Topic> topics) {
        if (ids == null || ids.size() < 3 || ids.size() > 5 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size() || roles == null || roles.size() != ids.size()
                || roles.stream().anyMatch(r -> r == null || !List.of("PRESIDENT", "SECRETARY", "MEMBER").contains(r))
                || roles.stream().filter("PRESIDENT"::equals).count() != 1
                || roles.stream().filter("SECRETARY"::equals).count() != 1) {
            throw new IllegalArgumentException("Hội đồng cần 3–5 giảng viên khác nhau, đúng một Chủ tịch và một Thư ký!");
        }
        Map<Long, Lecturer> lecturers = lecturerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Lecturer::getId, l -> l));
        List<Lecturer> ordered = new ArrayList<>();
        for (Long id : ids) {
            Lecturer lecturer = lecturers.get(id);
            if (lecturer == null || lecturer.getUser() == null || !Boolean.TRUE.equals(lecturer.getUser().getIsActive())
                    || lecturer.getDepartment() == null || department == null
                    || !Objects.equals(lecturer.getDepartment().getId(), department.getId())
                    || topics.stream().anyMatch(t -> t.isSupervisorOrCoSupervisor(lecturer))) {
                throw new IllegalArgumentException("Thành viên phải là giảng viên đang hoạt động, cùng khoa và không hướng dẫn đề tài được phân công!");
            }
            ordered.add(lecturer);
        }
        return ordered;
    }

    public List<Topic> getTopicsInCouncil(Long councilId) {
        return topicRepository.findByCouncilId(councilId);
    }

    public List<Topic> getTopicsByCouncil(Long councilId) {
        return topicRepository.findByCouncilId(councilId);
    }

    public List<Topic> getTopicsByReviewer(Long reviewerId) {
        return topicRepository.findByReviewerId(reviewerId);
    }

    public List<CouncilMember> getCouncilMembers(Long councilId) {
        return councilMemberRepository.findByCouncilId(councilId);
    }

    public Map<Long, Set<Long>> getCouncilLecturerIdsMap(List<Long> councilIds) {
        if (councilIds == null || councilIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CouncilMember> allMembers = councilMemberRepository.findByCouncilIdIn(councilIds);
        Map<Long, Set<Long>> result = new HashMap<>();
        for (Long cId : councilIds) {
            result.put(cId, new HashSet<>());
        }
        for (CouncilMember m : allMembers) {
            if (m.getCouncil() != null && m.getLecturer() != null) {
                result.computeIfAbsent(m.getCouncil().getId(), k -> new HashSet<>()).add(m.getLecturer().getId());
            }
        }
        return result;
    }

    public List<CouncilMember> getMembersByCouncil(Long councilId) {
        return councilMemberRepository.findByCouncilId(councilId);
    }

    public List<Evaluation> getEvaluationsForTopic(Long topicId) {
        Topic topic = topicRepository.findById(topicId).orElse(null);
        if (topic == null) return List.of();
        List<StudentGroup> groups = groupRepository.findByTopicId(topicId);
        if (groups.size() != 1) return List.of();
        StudentGroup group = groups.get(0);
        return filterCurrentEvaluations(topic, group, evaluationRepository.findByGroupId(group.getId()), councilLecturerIds(topic));
    }

    public List<Evaluation> getEvaluationsByTopic(Long topicId) {
        return getEvaluationsForTopic(topicId);
    }

    public Optional<Evaluation> getEvaluation(Long topicId, Long lecturerId, String evaluationType) {
        return getEvaluationsForTopic(topicId).stream()
                .filter(e -> Objects.equals(e.getLecturer().getId(), lecturerId)
                        && e.getEvaluationType().equalsIgnoreCase(evaluationType)).findFirst();
    }

    private Set<Long> supervisorIds(Topic topic) {
        Set<Long> ids = new HashSet<>();
        if (topic.getSupervisor() != null) ids.add(topic.getSupervisor().getId());
        if (topic.getCoSupervisor() != null) ids.add(topic.getCoSupervisor().getId());
        return ids;
    }

    private Set<Long> councilLecturerIds(Topic topic) {
        if (topic.getCouncil() == null) return Set.of();
        return councilMemberRepository.findByCouncilId(topic.getCouncil().getId()).stream()
                .filter(m -> m.getLecturer() != null).map(m -> m.getLecturer().getId()).collect(Collectors.toSet());
    }

    private List<Evaluation> filterCurrentEvaluations(Topic topic, StudentGroup group, List<Evaluation> evaluations, Set<Long> councilIds) {
        if (group == null || group.getTopic() == null || !Objects.equals(group.getTopic().getId(), topic.getId())
                || group.getPeriod() == null || topic.getPeriod() == null
                || !Objects.equals(group.getPeriod().getId(), topic.getPeriod().getId())
                || group.getStatus() == null
                || List.of("DRAFT", "REJECTED", "DISSOLVED").contains(group.getStatus())) return List.of();
        Set<Long> supervisors = supervisorIds(topic);
        return evaluations.stream().filter(e -> e.getGroup() != null && Objects.equals(e.getGroup().getId(), group.getId())
                && e.getTopic() != null && Objects.equals(e.getTopic().getId(), topic.getId())
                && Boolean.TRUE.equals(e.getIsSubmitted()) && e.getScore() != null && Double.isFinite(e.getScore())
                && e.getScore() >= 0 && e.getScore() <= 10 && e.getLecturer() != null && e.getLecturer().getId() != null)
                .filter(e -> switch (Objects.toString(e.getEvaluationType(), "").toUpperCase(Locale.ROOT)) {
                    case "SUPERVISOR" -> supervisors.contains(e.getLecturer().getId());
                    case "REVIEWER" -> topic.getReviewer() != null && !supervisors.contains(e.getLecturer().getId())
                            && Objects.equals(topic.getReviewer().getId(), e.getLecturer().getId());
                    case "COUNCIL" -> !supervisors.contains(e.getLecturer().getId()) && councilIds.contains(e.getLecturer().getId());
                    default -> false;
                }).toList();
    }

    private boolean hasAllGrades(List<Evaluation> evaluations, String type, Set<Long> expectedIds) {
        return !expectedIds.isEmpty() && evaluations.stream().filter(e -> type.equalsIgnoreCase(e.getEvaluationType()))
                .map(e -> e.getLecturer().getId()).collect(Collectors.toSet()).containsAll(expectedIds);
    }

    public boolean isTopicFinalized(Long topicId) {
        if (topicId == null) return false;
        return topicRepository.findById(topicId)
                .map(t -> Boolean.TRUE.equals(t.getIsFinalized()))
                .orElse(false);
    }

    @Transactional
    public boolean submitEvaluation(Long topicId, Long lecturerId, String evaluationType, Double score, String comments) {
        Topic topic = topicRepository.findForPublication(topicId).orElse(null);
        Lecturer lecturer = lecturerRepository.findById(lecturerId).orElse(null);

        if (topic == null || lecturer == null || evaluationType == null) return false;
        entityManager.refresh(topic);

        // Khóa sửa điểm nếu đề tài đã được công bố kết quả chính thức cho sinh viên (isFinalized = true)
        if (Boolean.TRUE.equals(topic.getIsFinalized())) {
            System.err.println("[DB-EVAL] Lỗi! Đề tài ID=" + topicId + " đã được công bố kết quả điểm chính thức. Yêu cầu Trưởng khoa/bộ môn thu hồi công bố trước khi chỉnh sửa điểm!");
            return false;
        }

        if (score == null || !Double.isFinite(score) || score < 0.0 || score > 10.0) {
            System.err.println("[DB-EVAL] Điểm không hợp lệ: " + score + " (Điểm phải nằm trong thang điểm từ 0.0 đến 10.0)!");
            return false;
        }

        List<StudentGroup> groups = groupRepository.findByTopicId(topicId);
        if (groups.size() != 1) {
            System.err.println("[DB-EVAL] Lỗi! Đề tài ID=" + topicId + " chưa có nhóm sinh viên đăng ký thực hiện, tuyệt đối không được chấm điểm!");
            return false;
        }

        String evalTypeUpper = evaluationType.trim().toUpperCase();
        if ("REVIEWER".equals(evalTypeUpper) && topic.getPeriod() != null
                && topic.getPeriod().isReviewerDeadlinePassed()) return false;

        // 1. Phân quyền và kiểm tra xung đột lợi ích nghiêm ngặt theo quy chế
        boolean isSupervisor = topic.isSupervisorOrCoSupervisor(lecturer);

        if ("COUNCIL".equals(evalTypeUpper)) {
            // TUYỆT ĐỐI KHÔNG ĐỂ GVHD (chính hoặc phụ) CÓ QUYỀN CHẤM ĐIỂM TRONG HỘI ĐỒNG BẢO VỆ
            if (isSupervisor) {
                System.err.println("[DB-EVAL] Xung đột lợi ích nghiêm trọng! GVHD không được phép chấm điểm trong Hội đồng bảo vệ cho đề tài của mình!");
                return false;
            }
            // Giảng viên chấm Hội đồng phải thuộc Hội đồng được phân công cho đề tài này
            if (topic.getCouncil() == null) {
                System.err.println("[DB-EVAL] Lỗi! Đề tài chưa được phân công vào Hội đồng bảo vệ nào!");
                return false;
            }
            List<CouncilMember> members = councilMemberRepository.findByCouncilId(topic.getCouncil().getId());
            boolean isMember = members.stream()
                    .anyMatch(m -> m.getLecturer() != null && m.getLecturer().getId().equals(lecturer.getId()));
            if (!isMember) {
                System.err.println("[DB-EVAL] Lỗi! Giảng viên không phải thành viên của Hội đồng ID=" + topic.getCouncil().getId());
                return false;
            }
        } else if ("REVIEWER".equals(evalTypeUpper)) {
            // Giảng viên phản biện tuyệt đối không được trùng với GVHD
            if (isSupervisor) {
                System.err.println("[DB-EVAL] Xung đột lợi ích! GVHD không được tự chấm phản biện cho đề tài của mình!");
                return false;
            }
            // Giảng viên phản biện phải là người được phân công chính thức cho đề tài
            if (topic.getReviewer() == null || !topic.getReviewer().getId().equals(lecturer.getId())) {
                System.err.println("[DB-EVAL] Lỗi quyền hạn! Giảng viên không phải người phản biện được phân công cho đề tài này!");
                return false;
            }
        } else if ("SUPERVISOR".equals(evalTypeUpper)) {
            // Chỉ GVHD chính hoặc phụ mới có quyền nhập điểm quá trình (SUPERVISOR)
            if (!isSupervisor) {
                System.err.println("[DB-EVAL] Lỗi quyền hạn! Chỉ Giảng viên hướng dẫn của đề tài mới được chấm điểm quá trình (SUPERVISOR)!");
                return false;
            }
        } else {
            System.err.println("[DB-EVAL] Loại phiếu chấm không hợp lệ: " + evaluationType);
            return false;
        }

        Evaluation eval = evaluationRepository.findByTopicIdAndGroupIdAndLecturerIdAndEvaluationType(
                        topicId, groups.get(0).getId(), lecturerId, evalTypeUpper)
                .orElse(new Evaluation());

        eval.setTopic(topic);
        eval.setLecturer(lecturer);
        eval.setGroup(!groups.isEmpty() ? groups.get(0) : null);
        eval.setEvaluationType(evalTypeUpper);
        eval.setScore(score);
        eval.setComments(comments != null ? comments.trim() : null);
        eval.setIsSubmitted(true);
        eval.setEvaluatedAt(OffsetDateTime.now());

        evaluationRepository.save(eval);

        calculateAndSetFinalScore(topic);
        return true;
    }

    /**
     * Tính điểm tổng kết theo chuẩn quy chế học thuật bất biến V2.0:
     * - Chỉ sử dụng Trung bình cộng (Arithmetic Mean).
     * - CẤM TUYỆT ĐỐI trọng số phần trăm cố định (40%-60%, 30%-70%).
     * - Làm tròn số học đến 2 chữ số thập phân (0.01) sử dụng BigDecimal với RoundingMode.HALF_UP.
     * 
     * Phân nhánh theo loại đợt (PeriodType):
     * 1. MON_HOC / NCKH: FinalScore = Score_GVHD (hoặc TBC nếu có 2 GVHD)
     * 2. TLCN: FinalScore = (Score_GVHD + Score_GVPB) / 2
     * 3. KLTN:
     *    Score_Council = (1/N) * sum(Score_Member_i) (N: số thành viên HĐ, 3 <= N <= 5)
     *    FinalScore = (Score_GVHD + Score_GVPB + Score_Council) / 3
     */
    @Transactional
    public void calculateAndSetFinalScore(Topic topic) {
        if (topic == null) return;

        List<StudentGroup> groups = groupRepository.findByTopicId(topic.getId());
        if (groups.isEmpty()) {
            topic.setAdvisorScore(null);
            topic.setReviewerScore(null);
            topic.setCouncilScore(null);
            topic.setFinalScore(null);
            topic.setIsFinalized(false);
            topicRepository.save(topic);
            return;
        }

        List<Evaluation> evals = getEvaluationsForTopic(topic.getId());
        if (evals.isEmpty()) {
            topic.setAdvisorScore(null);
            topic.setReviewerScore(null);
            topic.setCouncilScore(null);
            topic.setFinalScore(null);
            topicRepository.save(topic);
            return;
        }

        // 1. Điểm Giảng viên Hướng dẫn (SUPERVISOR) - TBC nếu có 2 GVHD
        List<Evaluation> supervisorEvals = evals.stream()
                .filter(e -> "SUPERVISOR".equalsIgnoreCase(e.getEvaluationType()) && e.getScore() != null)
                .toList();
        Double advisorScore = null;
        if (hasAllGrades(evals, "SUPERVISOR", supervisorIds(topic))) {
            double avg = supervisorEvals.stream().mapToDouble(Evaluation::getScore).average().orElse(0.0);
            advisorScore = roundScore(avg);
        }

        // 2. Điểm Giảng viên Phản biện (REVIEWER) - TBC nếu có nhiều phiếu
        List<Evaluation> reviewerEvals = evals.stream()
                .filter(e -> "REVIEWER".equalsIgnoreCase(e.getEvaluationType()) && e.getScore() != null)
                .toList();
        Double reviewerScore = null;
        if (!reviewerEvals.isEmpty()) {
            double avg = reviewerEvals.stream().mapToDouble(Evaluation::getScore).average().orElse(0.0);
            reviewerScore = roundScore(avg);
        }

        // 3. Điểm Hội đồng bảo vệ (COUNCIL) = TBC điểm của các thành viên HĐ (3-5 thành viên)
        // Chỉ tính điểm Hội đồng khi TẤT CẢ các thành viên được phân công trong Hội đồng đã chấm xong
        List<Evaluation> councilEvals = evals.stream()
                .filter(e -> "COUNCIL".equalsIgnoreCase(e.getEvaluationType()) && e.getScore() != null)
                .toList();
        Double councilScore = null;
        if (topic.getCouncil() != null) {
            Set<Long> councilIds = councilLecturerIds(topic);
            if (councilIds.size() >= 3 && councilIds.size() <= 5 && hasAllGrades(evals, "COUNCIL", councilIds)) {
                double avg = councilEvals.stream().mapToDouble(Evaluation::getScore).average().orElse(0.0);
                councilScore = roundScore(avg);
            }
        }

        topic.setAdvisorScore(advisorScore);
        topic.setReviewerScore(reviewerScore);
        topic.setCouncilScore(councilScore);

        // 4. ÁP DỤNG CÔNG THỨC ĐIỂM TỔNG KẾT (FinalScore) THEO PERIOD TYPE (TRUNG BÌNH CỘNG)
        RegistrationPeriod period = topic.getPeriod();
        String periodType = (period != null && period.getPeriodType() != null) 
                ? period.getPeriodType().trim().toUpperCase() 
                : "KLTN";

        Double finalScore = null;
        switch (periodType) {
            case "MON_HOC":
            case "NCKH":
                if (advisorScore != null) {
                    finalScore = advisorScore;
                }
                break;
            case "TLCN":
                if (advisorScore != null && reviewerScore != null) {
                    finalScore = roundScore((advisorScore + reviewerScore) / 2.0);
                }
                break;
            case "KLTN":
            default:
                if (advisorScore != null && reviewerScore != null && councilScore != null) {
                    // Đầy đủ 3 trạm: GVHD + GVPB + Hội đồng
                    finalScore = roundScore((advisorScore + reviewerScore + councilScore) / 3.0);
                }
                break;
        }

        topic.setFinalScore(finalScore);
        topicRepository.save(topic);
    }

    private Double roundScore(double val) {
        return java.math.BigDecimal.valueOf(val)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Kiểm tra đề tài đã hoàn tất đầy đủ tất cả các phiếu chấm bắt buộc theo quy chế đợt chưa.
     */
    public boolean isTopicFullyGraded(Topic topic) {
        if (topic == null || topic.getId() == null) return false;
        List<Evaluation> evaluations = getEvaluationsForTopic(topic.getId());
        if (!hasAllGrades(evaluations, "SUPERVISOR", supervisorIds(topic))) return false;
        String type = topic.getPeriod() != null ? topic.getPeriod().getPeriodType() : null;
        if ("MON_HOC".equalsIgnoreCase(type) || "NCKH".equalsIgnoreCase(type)) return topic.getFinalScore() != null;
        if (topic.getReviewer() == null || !hasAllGrades(evaluations, "REVIEWER", Set.of(topic.getReviewer().getId()))) return false;
        if ("TLCN".equalsIgnoreCase(type)) return topic.getFinalScore() != null;
        Set<Long> councilIds = councilLecturerIds(topic);
        return "KLTN".equalsIgnoreCase(type) && councilIds.size() >= 3 && councilIds.size() <= 5
                && hasAllGrades(evaluations, "COUNCIL", councilIds) && topic.getFinalScore() != null;
    }

    /**
     * Công bố kết quả điểm số cho sinh viên và tự động gửi email thông báo theo Rubric.
     */
    public record PublicationResult(int published, int sent, int failed) {}
    private record PublicationTarget(Topic topic, List<StudentGroup> groups) {}

    public PublicationResult publishTopicResults(Long topicId, Boolean isFinalized) {
        PublicationTarget target = publicationTransaction.execute(status -> {
            Topic topic = topicRepository.findForPublication(topicId).orElse(null);
            if (topic == null) return null;
            entityManager.refresh(topic);
            if (Boolean.TRUE.equals(topic.getIsFinalized()) == Boolean.TRUE.equals(isFinalized)) return null;
            List<StudentGroup> groups = groupRepository.findByTopicId(topicId);
            if (Boolean.TRUE.equals(isFinalized)) calculateAndSetFinalScore(topic);
            if (groups.isEmpty() || (Boolean.TRUE.equals(isFinalized) && !isTopicFullyGraded(topic))) return null;
            topic.setIsFinalized(Boolean.TRUE.equals(isFinalized));
            topicRepository.saveAndFlush(topic);
            return new PublicationTarget(topic, groups);
        });
        if (target == null) return new PublicationResult(0, 0, 0);

        // Chỉ gửi sau khi commit; khóa đề tài ngăn hai lần công bố đồng thời gửi trùng.
        int sent = 0;
        int failed = 0;
        if (Boolean.TRUE.equals(isFinalized)) {
            for (StudentGroup g : target.groups()) {
                int groupMemberCount = (g.getLeader() != null ? 1 : 0) + (g.getMembers() != null ? g.getMembers().size() : 0);
                sent += Math.max(1, groupMemberCount);
                emailService.sendTopicResultEmailsToGroup(target.topic().getId(), g.getId());
            }
        }

        return new PublicationResult(1, sent, failed);
    }

    public PublicationResult publishAllResults(Long departmentId, Long periodId, Boolean isFinalized) {
        List<Topic> topics = (departmentId != null) 
                ? topicRepository.findByDepartmentId(departmentId) 
                : topicRepository.findAll();
        if (topics.isEmpty()) return new PublicationResult(0, 0, 0);

        List<Long> topicIds = topics.stream().map(Topic::getId).filter(Objects::nonNull).toList();
        List<StudentGroup> allGroups = groupRepository.findByTopicIdIn(topicIds);
        Set<Long> topicsWithGroups = allGroups.stream()
                .filter(g -> g.getTopic() != null)
                .map(g -> g.getTopic().getId())
                .collect(Collectors.toSet());

        int count = 0;
        int sent = 0;
        int failed = 0;
        for (Topic t : topics) {
            if (periodId == null || (t.getPeriod() != null && t.getPeriod().getId().equals(periodId))) {
                if ("APPROVED".equalsIgnoreCase(t.getStatus()) && topicsWithGroups.contains(t.getId())) {
                    PublicationResult result = publishTopicResults(t.getId(), isFinalized);
                    count += result.published();
                    sent += result.sent();
                    failed += result.failed();
                }
            }
        }
        System.out.println("[DB-EVAL] Đã cập nhật công bố toàn đợt cho " + count + " đề tài hợp lệ (isFinalized=" + isFinalized + ")");
        return new PublicationResult(count, sent, failed);
    }

    public Map<Long, List<Evaluation>> getEvaluationsMapForTopics(List<Topic> topics) {
        Map<Long, List<Evaluation>> result = new HashMap<>();
        if (topics == null || topics.isEmpty()) return result;
        Map<Long, StudentGroup> groups = getGroupsMapForTopics(topics);
        List<Long> groupIds = groups.values().stream().map(StudentGroup::getId).toList();
        Map<Long, List<Evaluation>> evaluations = groupIds.isEmpty() ? Map.of()
                : evaluationRepository.findByGroupIdIn(groupIds).stream()
                    .collect(Collectors.groupingBy(e -> e.getGroup().getId()));
        List<Long> councilIds = topics.stream().filter(t -> t.getCouncil() != null)
                .map(t -> t.getCouncil().getId()).distinct().toList();
        Map<Long, Set<Long>> councilLecturers = getCouncilLecturerIdsMap(councilIds);
        for (Topic topic : topics) {
            StudentGroup group = groups.get(topic.getId());
            Set<Long> ids = topic.getCouncil() != null ? councilLecturers.getOrDefault(topic.getCouncil().getId(), Set.of()) : Set.of();
            result.put(topic.getId(), filterCurrentEvaluations(topic, group,
                    group != null ? evaluations.getOrDefault(group.getId(), List.of()) : List.of(), ids));
        }
        return result;
    }

    public Map<Long, StudentGroup> getGroupsMapForTopics(List<Topic> topics) {
        Map<Long, StudentGroup> map = new HashMap<>();
        if (topics == null || topics.isEmpty()) return map;

        List<Long> topicIds = topics.stream().map(Topic::getId).filter(Objects::nonNull).toList();
        if (topicIds.isEmpty()) return map;

        List<StudentGroup> allGroups = groupRepository.findByTopicIdIn(topicIds);
        for (StudentGroup g : allGroups) {
            if (g.getTopic() != null && !map.containsKey(g.getTopic().getId())) {
                map.put(g.getTopic().getId(), g);
            }
        }
        return map;
    }

    /**
     * Đếm số đề tài trong các Hội đồng mà người dùng (với tư cách Giảng viên) là thành viên,
     * đang có đề tài được phân công và người này chưa nộp phiếu chấm COUNCIL (loại trừ đề tài do chính mình hướng dẫn).
     */
    @Transactional(readOnly = true)
    public int countPendingCouncilGradingForUser(AppUser user) {
        if (user == null || user.getId() == null) return 0;
        Lecturer lecturer = lecturerRepository.findByUserId(user.getId()).orElse(null);
        if (lecturer == null) return 0;

        List<Council> myCouncils = councilRepository.findByLecturerId(lecturer.getId());
        if (myCouncils == null || myCouncils.isEmpty()) return 0;

        List<Long> councilIds = myCouncils.stream().map(Council::getId).filter(Objects::nonNull).toList();
        if (councilIds.isEmpty()) return 0;

        List<Topic> assignedTopics = topicRepository.findByCouncilIdIn(councilIds);
        if (assignedTopics == null || assignedTopics.isEmpty()) return 0;

        Set<Long> gradedTopicIds = evaluationRepository.findByLecturerId(lecturer.getId()).stream()
                .filter(e -> "COUNCIL".equalsIgnoreCase(e.getEvaluationType()) && e.getScore() != null && e.getTopic() != null)
                .map(e -> e.getTopic().getId())
                .collect(Collectors.toSet());

        Set<Long> pendingTopicIds = new HashSet<>();
        for (Topic t : assignedTopics) {
            if (t.isSupervisorOrCoSupervisor(lecturer)) {
                continue;
            }
            if (!gradedTopicIds.contains(t.getId())) {
                pendingTopicIds.add(t.getId());
            }
        }
        return pendingTopicIds.size();
    }
}