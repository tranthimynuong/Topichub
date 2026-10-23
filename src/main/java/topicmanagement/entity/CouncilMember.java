package topicmanagement.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "council_members", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_council_member_lecturer",
        columnNames = {"council_id", "lecturer_id"}
    )
})
public class CouncilMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "council_id", nullable = false)
    private Council council;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    @Column(name = "council_role", nullable = false, length = 50)
    private String councilRole = "MEMBER"; // PRESIDENT, SECRETARY, MEMBER

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public CouncilMember() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Council getCouncil() {
        return council;
    }

    public void setCouncil(Council council) {
        this.council = council;
    }

    public Lecturer getLecturer() {
        return lecturer;
    }

    public void setLecturer(Lecturer lecturer) {
        this.lecturer = lecturer;
    }

    public String getCouncilRole() {
        return councilRole;
    }

    public void setCouncilRole(String councilRole) {
        this.councilRole = councilRole;
    }

    public String getRoleName() {
        if (councilRole == null) return "Ủy viên";
        return switch (councilRole.toUpperCase()) {
            case "PRESIDENT" -> "Chủ tịch Hội đồng";
            case "SECRETARY" -> "Thư ký";
            case "REVIEWER" -> "Ủy viên Phản biện";
            default -> "Ủy viên";
        };
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
