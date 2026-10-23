package topicmanagement.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "lecturers")
public class Lecturer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "lecturer_code", length = 50)
    private String lecturerCode;

    @Column(name = "academic_title", length = 50)
    private String academicTitle;

    @Column(length = 50)
    private String degree;

    @Column(name = "max_topics_quota")
    private Integer maxTopicsQuota = 5;

    public Lecturer() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getLecturerCode() {
        return lecturerCode;
    }

    public void setLecturerCode(String lecturerCode) {
        this.lecturerCode = lecturerCode;
    }

    public String getAcademicTitle() {
        return academicTitle;
    }

    public void setAcademicTitle(String academicTitle) {
        this.academicTitle = academicTitle;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public Integer getMaxTopicsQuota() {
        return maxTopicsQuota;
    }

    public void setMaxTopicsQuota(Integer maxTopicsQuota) {
        this.maxTopicsQuota = maxTopicsQuota;
    }

    public String getAcademicRank() {
        if (degree != null && !degree.trim().isEmpty()) {
            return degree;
        }
        if (academicTitle != null && !academicTitle.trim().isEmpty()) {
            return academicTitle;
        }
        return "GV";
    }
}
