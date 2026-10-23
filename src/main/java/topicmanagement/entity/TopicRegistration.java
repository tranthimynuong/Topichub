package topicmanagement.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Ghi nhận lịch sử đăng ký đề tài của một NHÓM sinh viên.
 * Đây là audit log — mỗi nhóm chỉ có một bản ghi active (APPROVED) tại một thời điểm.
 *
 * Lưu ý: Source of truth chính vẫn là StudentGroup.topic (FK trực tiếp).
 * Bảng này phục vụ việc truy vết: ai đăng ký lúc nào, lý do bị từ chối là gì.
 */
@Entity
@Table(name = "topic_registrations", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_topic_registration_group",
        columnNames = {"topic_id", "group_id"}
    )
}, indexes = {
    @Index(name = "idx_topic_reg_topic_id", columnList = "topic_id"),
    @Index(name = "idx_topic_reg_group_id", columnList = "group_id"),
    @Index(name = "idx_topic_reg_status", columnList = "status"),
    @Index(name = "idx_topic_reg_topic_status", columnList = "topic_id, status")
})
public class TopicRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /**
     * BUG-J FIX: Đổi từ student_id → group_id.
     * Đăng ký đề tài là hành động của NHÓM, không phải cá nhân sinh viên.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private StudentGroup group;

    /**
     * Nhóm trưởng thực hiện hành động đăng ký (để audit).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_student_id")
    private Student registeredBy;

    @Column(length = 50)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "registered_at")
    private OffsetDateTime registeredAt = OffsetDateTime.now();

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    public TopicRegistration() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public StudentGroup getGroup() {
        return group;
    }

    public void setGroup(StudentGroup group) {
        this.group = group;
    }

    public Student getRegisteredBy() {
        return registeredBy;
    }

    public void setRegisteredBy(Student registeredBy) {
        this.registeredBy = registeredBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public OffsetDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(OffsetDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
}
