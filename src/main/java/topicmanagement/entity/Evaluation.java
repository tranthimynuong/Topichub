package topicmanagement.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "evaluations", uniqueConstraints = @UniqueConstraint(
        name = "uk_evaluation_group_lecturer_type",
        columnNames = {"topic_id", "group_id", "lecturer_id", "evaluation_type"}))
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private StudentGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    @Column(name = "evaluation_type", nullable = false, length = 50)
    private String evaluationType; // SUPERVISOR, REVIEWER, COUNCIL

    private Double score;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "is_submitted", nullable = false)
    private Boolean isSubmitted = false;

    @Column(name = "evaluated_at")
    private OffsetDateTime evaluatedAt = OffsetDateTime.now();

    public Evaluation() {}

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

    public Lecturer getLecturer() {
        return lecturer;
    }

    public void setLecturer(Lecturer lecturer) {
        this.lecturer = lecturer;
    }

    public String getEvaluationType() {
        return evaluationType;
    }

    public void setEvaluationType(String evaluationType) {
        this.evaluationType = evaluationType;
    }

    public String getEvaluationTypeName() {
        if (evaluationType == null) return "";
        return switch (evaluationType.toUpperCase()) {
            case "SUPERVISOR" -> "Giảng viên Hướng dẫn";
            case "REVIEWER" -> "Giảng viên Phản biện";
            case "COUNCIL" -> "Hội đồng Đánh giá";
            default -> evaluationType;
        };
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Boolean getIsSubmitted() {
        return isSubmitted;
    }

    public void setIsSubmitted(Boolean isSubmitted) {
        this.isSubmitted = isSubmitted;
    }

    public OffsetDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(OffsetDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public static Double calculateCouncilDiscrepancy(java.util.List<Evaluation> evals) {
        if (evals == null || evals.isEmpty()) return 0.0;
        java.util.List<Double> scores = evals.stream()
                .filter(e -> "COUNCIL".equalsIgnoreCase(e.getEvaluationType()) && e.getScore() != null)
                .map(Evaluation::getScore)
                .toList();
        if (scores.size() < 2) return 0.0;
        double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        return java.math.BigDecimal.valueOf(max - min).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
