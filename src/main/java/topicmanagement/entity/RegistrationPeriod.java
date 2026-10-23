package topicmanagement.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Entity
@Table(name = "registration_periods")
public class RegistrationPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Tên đợt đăng ký không được để trống")
    @Size(max = 255, message = "Tên đợt tối đa 255 ký tự")
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank(message = "Loại đợt đăng ký không được để trống")
    @Size(max = 50)
    @Column(name = "period_type", nullable = false, length = 50)
    private String periodType; // MON_HOC, NCKH, TLCN, KLTN

    @Column(name = "academic_year", nullable = false, length = 50)
    private String academicYear = "2026-2027";

    @Column(nullable = false)
    private Integer semester = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "lecturer_proposal_start")
    private OffsetDateTime lecturerProposalStart;

    @Column(name = "lecturer_proposal_end")
    private OffsetDateTime lecturerProposalEnd;

    @Column(name = "student_registration_start")
    private OffsetDateTime studentRegistrationStart;

    @Column(name = "student_registration_end")
    private OffsetDateTime studentRegistrationEnd;

    @Column(name = "reviewer_deadline")
    private OffsetDateTime reviewerDeadline;

    @Column(name = "council_defense_date")
    private OffsetDateTime councilDefenseDate;

    @Column(name = "min_students")
    private Integer minStudents = 3;

    @Column(name = "max_students")
    private Integer maxStudents = 5;

    @Column(name = "min_topics_per_lecturer")
    private Integer minTopicsPerLecturer = 3;

    @Column(nullable = false, length = 50)
    private String status = "OPEN"; // DRAFT, OPEN, IN_PROGRESS, COMPLETED, CLOSED

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public RegistrationPeriod() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getPeriodTypeName() {
        if (periodType == null) return "";
        return switch (periodType.toUpperCase()) {
            case "MON_HOC" -> "Đồ án Môn học";
            case "NCKH" -> "Nghiên cứu Khoa học";
            case "TLCN" -> "Tiểu luận Chuyên ngành";
            case "KLTN" -> "Khóa luận Tốt nghiệp";
            default -> periodType;
        };
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    @Transient
    public Long getDepartmentId() {
        return department != null ? department.getId() : null;
    }

    public OffsetDateTime getLecturerProposalStart() {
        return lecturerProposalStart;
    }

    public void setLecturerProposalStart(OffsetDateTime lecturerProposalStart) {
        this.lecturerProposalStart = lecturerProposalStart;
    }

    public OffsetDateTime getLecturerProposalEnd() {
        return lecturerProposalEnd;
    }

    public void setLecturerProposalEnd(OffsetDateTime lecturerProposalEnd) {
        this.lecturerProposalEnd = lecturerProposalEnd;
    }

    public OffsetDateTime getStudentRegistrationStart() {
        return studentRegistrationStart;
    }

    public void setStudentRegistrationStart(OffsetDateTime studentRegistrationStart) {
        this.studentRegistrationStart = studentRegistrationStart;
    }

    public OffsetDateTime getStudentRegistrationEnd() {
        return studentRegistrationEnd;
    }

    public void setStudentRegistrationEnd(OffsetDateTime studentRegistrationEnd) {
        this.studentRegistrationEnd = studentRegistrationEnd;
    }

    public OffsetDateTime getReviewerDeadline() {
        return reviewerDeadline;
    }

    public void setReviewerDeadline(OffsetDateTime reviewerDeadline) {
        this.reviewerDeadline = reviewerDeadline;
    }

    public OffsetDateTime getCouncilDefenseDate() {
        return councilDefenseDate;
    }

    public void setCouncilDefenseDate(OffsetDateTime councilDefenseDate) {
        this.councilDefenseDate = councilDefenseDate;
    }

    public Integer getMinStudents() {
        return minStudents != null && minStudents > 0 ? minStudents : 2;
    }

    public void setMinStudents(Integer minStudents) {
        this.minStudents = minStudents;
    }

    public Integer getMaxStudents() {
        return maxStudents != null && maxStudents > 0 ? maxStudents : 3;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
    }

    public Integer getMinTopicsPerLecturer() {
        return minTopicsPerLecturer != null && minTopicsPerLecturer > 0 ? minTopicsPerLecturer : 3;
    }

    public void setMinTopicsPerLecturer(Integer minTopicsPerLecturer) {
        this.minTopicsPerLecturer = minTopicsPerLecturer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Helper checks
    public boolean isLecturerProposalOpen() {
        OffsetDateTime now = OffsetDateTime.now();
        if (lecturerProposalStart != null && now.isBefore(lecturerProposalStart)) return false;
        if (lecturerProposalEnd != null && now.isAfter(lecturerProposalEnd)) return false;
        return Boolean.TRUE.equals(isActive) && !"CLOSED".equalsIgnoreCase(status);
    }

    public boolean isStudentRegistrationOpen() {
        OffsetDateTime now = OffsetDateTime.now();
        if (studentRegistrationStart != null && now.isBefore(studentRegistrationStart)) return false;
        if (studentRegistrationEnd != null && now.isAfter(studentRegistrationEnd)) return false;
        return Boolean.TRUE.equals(isActive) && !"CLOSED".equalsIgnoreCase(status);
    }

    public String getDisplayStatus() {
        if (!Boolean.TRUE.equals(isActive) || "CLOSED".equalsIgnoreCase(status)) {
            return "CLOSED";
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (councilDefenseDate != null && now.isAfter(councilDefenseDate)) {
            return "COMPLETED";
        }
        if (lecturerProposalStart != null && now.isBefore(lecturerProposalStart)) {
            return "UPCOMING";
        }
        if (lecturerProposalStart != null && lecturerProposalEnd != null
                && !now.isBefore(lecturerProposalStart) && !now.isAfter(lecturerProposalEnd)) {
            return "PROPOSAL_OPEN";
        }
        if (lecturerProposalEnd != null && studentRegistrationStart != null
                && now.isAfter(lecturerProposalEnd) && now.isBefore(studentRegistrationStart)) {
            return "PENDING_REGISTRATION";
        }
        if (studentRegistrationStart != null && studentRegistrationEnd != null
                && !now.isBefore(studentRegistrationStart) && !now.isAfter(studentRegistrationEnd)) {
            return "REGISTRATION_OPEN";
        }
        if (reviewerDeadline != null && studentRegistrationEnd != null
                && now.isAfter(studentRegistrationEnd) && !now.isAfter(reviewerDeadline)) {
            return "REVIEWING";
        }
        if (councilDefenseDate != null && reviewerDeadline != null
                && now.isAfter(reviewerDeadline) && !now.isAfter(councilDefenseDate)) {
            return "DEFENSE_WAITING";
        }
        if (studentRegistrationEnd != null && now.isAfter(studentRegistrationEnd)) {
            return "REGISTRATION_ENDED";
        }
        return "OPEN";
    }

    public boolean isProposalPassed() {
        return lecturerProposalEnd != null && OffsetDateTime.now().isAfter(lecturerProposalEnd);
    }

    public boolean isRegistrationPassed() {
        return studentRegistrationEnd != null && OffsetDateTime.now().isAfter(studentRegistrationEnd);
    }

    public boolean isReviewerDeadlinePassed() {
        return reviewerDeadline != null && OffsetDateTime.now().isAfter(reviewerDeadline);
    }

    public boolean isCouncilDefensePassed() {
        return councilDefenseDate != null && OffsetDateTime.now().isAfter(councilDefenseDate);
    }

    public String getDisplayStatusName() {
        return switch (getDisplayStatus()) {
            case "CLOSED" -> "Đã đóng";
            case "UPCOMING" -> "Sắp diễn ra";
            case "PROPOSAL_OPEN" -> "Đang mở Đề xuất (GV)";
            case "PENDING_REGISTRATION" -> "Chờ mở Đăng ký (SV)";
            case "REGISTRATION_OPEN" -> "Đang mở Đăng ký (SV)";
            case "REVIEWING" -> "Đang chấm GVPB";
            case "DEFENSE_WAITING" -> "Chờ bảo vệ Hội đồng";
            case "REGISTRATION_ENDED" -> "Hết hạn đăng ký";
            case "COMPLETED" -> "Đã kết thúc đợt";
            default -> "Đang hoạt động";
        };
    }
}
