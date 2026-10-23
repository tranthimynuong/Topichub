package topicmanagement.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "student_groups", indexes = {
    @Index(name = "idx_student_groups_period_id", columnList = "period_id"),
    @Index(name = "idx_student_groups_leader_id", columnList = "leader_id"),
    @Index(name = "idx_student_groups_topic_id", columnList = "topic_id"),
    @Index(name = "idx_student_groups_status", columnList = "status")
})
public class StudentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "period_id")
    private RegistrationPeriod period;

    @Column(name = "group_code", unique = true, nullable = false, length = 50)
    private String groupCode;

    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "leader_id")
    private Student leader;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "topic_id", unique = true)
    private Topic topic;

    @Column(nullable = false, length = 50)
    private String status = "DRAFT"; // DRAFT, REGISTERED, APPROVED, REJECTED, DEFENDING, COMPLETED

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<GroupMember> members = new ArrayList<>();

    public StudentGroup() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public RegistrationPeriod getPeriod() {
        return period;
    }

    public void setPeriod(RegistrationPeriod period) {
        this.period = period;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Student getLeader() {
        return leader;
    }

    public void setLeader(Student leader) {
        this.leader = leader;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<GroupMember> getMembers() {
        return members;
    }

    public void setMembers(List<GroupMember> members) {
        this.members = members;
    }

    public boolean isLeader(Student student) {
        if (student == null || leader == null) return false;
        return leader.getId().equals(student.getId());
    }

    public boolean hasMember(Student student) {
        if (student == null || student.getId() == null) return false;
        if (isLeader(student)) return true;
        if (members == null) return false;
        return members.stream().anyMatch(m -> m.getStudent() != null && student.getId().equals(m.getStudent().getId()));
    }

    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }
}
