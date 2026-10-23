package topicmanagement.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import topicmanagement.entity.*;
import topicmanagement.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DepartmentHeadService {

    private final TopicRepository topicRepository;
    private final DepartmentRepository departmentRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final CouncilRepository councilRepository;

    public DepartmentHeadService(
            TopicRepository topicRepository,
            DepartmentRepository departmentRepository,
            LecturerRepository lecturerRepository,
            StudentRepository studentRepository,
            RegistrationPeriodRepository periodRepository,
            StudentGroupRepository studentGroupRepository,
            CouncilRepository councilRepository) {
        this.topicRepository = topicRepository;
        this.departmentRepository = departmentRepository;
        this.lecturerRepository = lecturerRepository;
        this.studentRepository = studentRepository;
        this.periodRepository = periodRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.councilRepository = councilRepository;
    }

    public Department getDepartmentForUser(AppUser user) {
        if (user != null) {
            Optional<Lecturer> lecturerOpt = lecturerRepository.findByUserId(user.getId());
            if (lecturerOpt.isPresent() && lecturerOpt.get().getDepartment() != null) {
                return lecturerOpt.get().getDepartment();
            }
        }
        List<Department> depts = departmentRepository.findAll();
        return !depts.isEmpty() ? depts.get(0) : null;
    }

    // ==================== REGISTRATION PERIODS ====================

    public List<RegistrationPeriod> getAllPeriods(Long departmentId) {
        if (departmentId != null) {
            return periodRepository.findByDepartmentId(departmentId);
        }
        return periodRepository.findByIsActiveTrueOrderByIdDesc();
    }

    public RegistrationPeriod getPeriodById(Long id) {
        if (id == null) return null;
        return periodRepository.findById(id).orElse(null);
    }

    @Transactional
    public RegistrationPeriod createPeriod(Department dept, String name, String periodType, String academicYear, Integer semester,
                                         OffsetDateTime lecturerStart, OffsetDateTime lecturerEnd,
                                         OffsetDateTime studentStart, OffsetDateTime studentEnd,
                                         OffsetDateTime reviewerDeadline, OffsetDateTime councilDefenseDate,
                                         Integer minTopicsPerLecturer) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên đợt đăng ký không được để trống!");
        }

        RegistrationPeriod period = new RegistrationPeriod();
        period.setName(name.trim());
        period.setDepartment(dept);
        period.setPeriodType(periodType != null && !periodType.trim().isEmpty() ? periodType.trim() : "KLTN");
        period.setAcademicYear(academicYear != null && !academicYear.trim().isEmpty() ? academicYear.trim() : "2026-2027");
        period.setSemester(semester != null ? semester : 1);
        period.setLecturerProposalStart(lecturerStart);
        period.setLecturerProposalEnd(lecturerEnd);
        period.setStudentRegistrationStart(studentStart);
        period.setStudentRegistrationEnd(studentEnd);
        period.setReviewerDeadline(reviewerDeadline);
        period.setCouncilDefenseDate(councilDefenseDate);
        period.setMinTopicsPerLecturer(minTopicsPerLecturer != null ? minTopicsPerLecturer : 3);
        period.setMinStudents(3);
        period.setMaxStudents(5);
        period.setStatus("OPEN");
        period.setIsActive(true);
        period.setCreatedAt(OffsetDateTime.now());

        RegistrationPeriod saved = periodRepository.save(period);
        System.out.println("[DB-HEAD] Đã tạo đợt đăng ký mới vào DB: '" + saved.getName() + "' (ID=" + saved.getId() + ")");
        return saved;
    }

    @Transactional
    public RegistrationPeriod updatePeriod(Long id, Long departmentId, String name, String periodType, String academicYear, Integer semester,
                                         OffsetDateTime lecturerStart, OffsetDateTime lecturerEnd,
                                         OffsetDateTime studentStart, OffsetDateTime studentEnd,
                                         OffsetDateTime reviewerDeadline, OffsetDateTime councilDefenseDate,
                                         Integer minTopicsPerLecturer, String status) {
        RegistrationPeriod period = getPeriodById(id);
        if (period == null) return null;

        if (departmentId != null && period.getDepartment() != null
                && !departmentId.equals(period.getDepartment().getId())) {
            throw new SecurityException("Không có quyền chỉnh sửa đợt của khoa khác!");
        }

        if (name != null && !name.trim().isEmpty()) period.setName(name.trim());
        if (periodType != null && !periodType.trim().isEmpty()) period.setPeriodType(periodType.trim());
        if (academicYear != null && !academicYear.trim().isEmpty()) period.setAcademicYear(academicYear.trim());
        if (semester != null) period.setSemester(semester);
        period.setLecturerProposalStart(lecturerStart);
        period.setLecturerProposalEnd(lecturerEnd);
        period.setStudentRegistrationStart(studentStart);
        period.setStudentRegistrationEnd(studentEnd);
        period.setReviewerDeadline(reviewerDeadline);
        period.setCouncilDefenseDate(councilDefenseDate);
        if (minTopicsPerLecturer != null) period.setMinTopicsPerLecturer(minTopicsPerLecturer);
        if (status != null && !status.trim().isEmpty()) period.setStatus(status.trim());

        return periodRepository.save(period);
    }

    @Transactional
    public boolean updatePeriodStatus(Long periodId, String status, Long departmentId) {
        RegistrationPeriod p = getPeriodById(periodId);
        if (p == null) return false;
        if (departmentId != null && p.getDepartment() != null && !departmentId.equals(p.getDepartment().getId())) return false;
        if (status != null && !status.trim().isEmpty()) {
            p.setStatus(status.trim());
            periodRepository.save(p);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean deletePeriod(Long periodId, Long departmentId) {
        RegistrationPeriod p = getPeriodById(periodId);
        if (p == null) return false;
        if (departmentId != null && p.getDepartment() != null
                && !departmentId.equals(p.getDepartment().getId())) {
            return false;
        }
        List<Topic> topics = topicRepository.findByPeriodId(periodId);
        if (topics != null && !topics.isEmpty()) {
            p.setIsActive(false);
            p.setStatus("CLOSED");
            periodRepository.save(p);
            System.out.println("[DB-HEAD] Đợt ID=" + periodId + " có đề tài liên quan -> Đã ẩn đợt (isActive=false)");
            return true;
        }
        periodRepository.delete(p);
        System.out.println("[DB-HEAD] Đã xóa đợt ID=" + periodId);
        return true;
    }

    // ==================== TOPICS ====================

    public List<Topic> getDepartmentTopics(Long departmentId, String status, Long periodId, String keyword) {
        List<Topic> topics;
        if (departmentId != null) {
            topics = topicRepository.findByDepartmentId(departmentId);
        } else {
            topics = topicRepository.findAll();
        }

        return filterTopics(topics, status, periodId, keyword);
    }

    private List<Topic> filterTopics(List<Topic> topics, String status, Long periodId, String keyword) {
        return topics.stream()
                .filter(t -> {
                    if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
                        if (!status.equalsIgnoreCase(t.getStatus())) return false;
                    }
                    if (periodId != null) {
                        if (t.getPeriod() == null || !periodId.equals(t.getPeriod().getId())) return false;
                    }
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        String kw = keyword.trim().toLowerCase();
                        boolean matchTitle = t.getTitle() != null && t.getTitle().toLowerCase().contains(kw);
                        boolean matchCode = t.getCode() != null && t.getCode().toLowerCase().contains(kw);
                        boolean matchSupervisor = t.getSupervisor() != null && t.getSupervisor().getUser() != null &&
                                t.getSupervisor().getUser().getFullName() != null &&
                                t.getSupervisor().getUser().getFullName().toLowerCase().contains(kw);
                        return matchTitle || matchCode || matchSupervisor;
                    }
                    return true;
                })
                .sorted(Comparator
                        .comparing((Topic t) -> "PENDING".equalsIgnoreCase(t.getStatus()) ? 0 : 1)
                        .thenComparing(Topic::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Topic::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<Topic> getDepartmentTopicsPaged(Long departmentId, String status, Long periodId, String keyword, org.springframework.data.domain.Pageable pageable) {
        return topicRepository.findDepartmentTopicsPaged(departmentId, status, periodId, keyword, pageable);
    }

    public Topic getTopicById(Long topicId) {
        if (topicId == null) return null;
        return topicRepository.findById(topicId).orElse(null);
    }

    /**
     * IDOR Check: Phê duyệt đề tài theo thẩm quyền Khoa
     */
    @Transactional
    public boolean approveTopic(Long topicId, Long departmentId) {
        Optional<Topic> opt = topicRepository.findById(topicId);
        if (opt.isEmpty()) return false;
        Topic t = opt.get();

        if (!isTopicInScope(t, departmentId)) {
            System.err.println("[SECURITY-IDOR] Người dùng (dept=" + departmentId + ") cố duyệt đề tài ngoài phạm vi (topicId=" + topicId + ")");
            return false;
        }

        t.setStatus("APPROVED");
        t.setRejectionReason(null);
        topicRepository.save(t);
        System.out.println("[DB-HEAD] Phê duyệt đề tài ID=" + topicId + " thành công!");
        return true;
    }

    @Transactional
    public boolean quickEditAndApproveTopic(Long topicId, String title, String description, String requirements, Integer maxStudents, Long departmentId) {
        Optional<Topic> opt = topicRepository.findById(topicId);
        if (opt.isEmpty()) return false;
        Topic t = opt.get();
        if (!isTopicInScope(t, departmentId)) return false;
        if (title != null && !title.trim().isEmpty()) t.setTitle(title.trim());
        if (description != null) t.setDescription(description);
        if (requirements != null) t.setRequirements(requirements);
        if (maxStudents != null) t.setMaxStudents(maxStudents);
        t.setStatus("APPROVED");
        t.setRejectionReason(null);
        topicRepository.save(t);
        return true;
    }

    /**
     * IDOR Check: Từ chối đề tài theo thẩm quyền Khoa
     */
    @Transactional
    public boolean rejectTopic(Long topicId, String rejectionReason, Long departmentId) {
        Optional<Topic> opt = topicRepository.findById(topicId);
        if (opt.isEmpty()) return false;
        Topic t = opt.get();

        if (!isTopicInScope(t, departmentId)) {
            System.err.println("[SECURITY-IDOR] Người dùng (dept=" + departmentId + ") cố từ chối đề tài ngoài phạm vi (topicId=" + topicId + ")");
            return false;
        }

        t.setStatus("REJECTED");
        t.setRejectionReason(rejectionReason);
        topicRepository.save(t);
        System.out.println("[DB-HEAD] Từ chối đề tài ID=" + topicId + " (Lý do: " + rejectionReason + ")");
        return true;
    }

    /**
     * IDOR Helper kiểm tra đề tài có thuộc Department hay không
     */
    public boolean isTopicInScope(Topic topic, Long departmentId) {
        if (topic == null) return false;
        if (departmentId == null) return true;
        Department dept = topic.getDepartment();
        return dept != null && departmentId.equals(dept.getId());
    }

    // ==================== LECTURERS & STUDENTS ====================

    public List<Lecturer> getLecturersInDepartment(Long departmentId) {
        if (departmentId != null) {
            return lecturerRepository.findByDepartmentId(departmentId);
        }
        return lecturerRepository.findAll();
    }

    public List<Student> getStudentsInDepartment(Long departmentId) {
        if (departmentId != null) {
            return studentRepository.findByDepartmentId(departmentId);
        }
        return studentRepository.findAll();
    }

    public List<StudentGroup> getAllGroups() {
        return studentGroupRepository.findAll();
    }

    public List<StudentGroup> getGroupsInDepartmentAndPeriod(Long departmentId, Long periodId) {
        if (periodId != null) {
            return studentGroupRepository.findByPeriodId(periodId);
        }
        if (departmentId != null) {
            List<StudentGroup> list = studentGroupRepository.findByPeriodDepartmentId(departmentId);
            if (!list.isEmpty()) return list;
        }
        return studentGroupRepository.findAll();
    }

    public org.springframework.data.domain.Page<StudentGroup> getGroupsInDepartmentAndPeriodPaged(Long departmentId, Long periodId, org.springframework.data.domain.Pageable pageable) {
        if (periodId != null && departmentId != null) {
            return studentGroupRepository.findByPeriodDepartmentIdAndPeriodId(departmentId, periodId, pageable);
        } else if (periodId != null) {
            return studentGroupRepository.findByPeriodId(periodId, pageable);
        } else if (departmentId != null) {
            return studentGroupRepository.findByPeriodDepartmentId(departmentId, pageable);
        }
        return studentGroupRepository.findAll(pageable);
    }

    public Map<String, Object> getDepartmentStats(Long departmentId) {
        return getDepartmentStats(departmentId, null, null);
    }

    public Map<String, Object> getDepartmentStats(Long departmentId, Integer semester, String academicYear) {
        List<Topic> topics = getDepartmentTopics(departmentId, "ALL", null, null).stream()
                .filter(t -> {
                    if (semester != null) {
                        Integer tSem = t.getSemester() != null ? t.getSemester() : (t.getPeriod() != null ? t.getPeriod().getSemester() : null);
                        if (tSem != null && !tSem.equals(semester)) return false;
                    }
                    if (academicYear != null && !academicYear.isBlank()) {
                        String tYear = t.getAcademicYear() != null ? t.getAcademicYear() : (t.getPeriod() != null ? t.getPeriod().getAcademicYear() : null);
                        if (tYear != null && !tYear.equalsIgnoreCase(academicYear)) return false;
                    }
                    return true;
                })
                .toList();
        List<Lecturer> lecturers = getLecturersInDepartment(departmentId);
        List<Student> students = getStudentsInDepartment(departmentId);
        List<RegistrationPeriod> periods = getAllPeriods(departmentId).stream()
                .filter(p -> {
                    if (semester != null && p.getSemester() != null && !p.getSemester().equals(semester)) return false;
                    if (academicYear != null && !academicYear.isBlank() && p.getAcademicYear() != null && !p.getAcademicYear().equalsIgnoreCase(academicYear)) return false;
                    return true;
                })
                .toList();

        long pendingTopics = topics.stream().filter(t -> "PENDING".equalsIgnoreCase(t.getStatus())).count();
        long approvedTopics = topics.stream().filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus())).count();
        long rejectedTopics = topics.stream().filter(t -> "REJECTED".equalsIgnoreCase(t.getStatus())).count();

        long totalGroups = 0;
        for (RegistrationPeriod p : periods) {
            if (p.getId() != null) {
                totalGroups += studentGroupRepository.findByPeriodId(p.getId()).size();
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTopics", topics.size());
        stats.put("pendingTopics", pendingTopics);
        stats.put("approvedTopics", approvedTopics);
        stats.put("rejectedTopics", rejectedTopics);
        stats.put("totalLecturers", lecturers.size());
        stats.put("totalStudents", students.size());
        stats.put("totalPeriods", periods.size());
        stats.put("totalGroups", totalGroups);

        return stats;
    }
}
