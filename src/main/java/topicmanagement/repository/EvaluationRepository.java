package topicmanagement.repository;

import org.springframework.lang.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import topicmanagement.entity.Evaluation;

import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    @EntityGraph(attributePaths = {"topic", "lecturer", "lecturer.user", "group"})
    @Override
    @NonNull
    Optional<Evaluation> findById(@NonNull Long id);

    @EntityGraph(attributePaths = {"topic", "lecturer", "lecturer.user", "group"})
    List<Evaluation> findByTopicId(Long topicId);

    @EntityGraph(attributePaths = {"topic", "lecturer", "lecturer.user", "group"})
    List<Evaluation> findByTopicIdIn(List<Long> topicIds);

    @EntityGraph(attributePaths = {"topic", "lecturer", "lecturer.user", "group"})
    List<Evaluation> findByLecturerId(Long lecturerId);

    @EntityGraph(attributePaths = {"topic", "lecturer", "lecturer.user", "group"})
    List<Evaluation> findByGroupId(Long groupId);

    @EntityGraph(attributePaths = {"topic", "lecturer", "lecturer.user", "group"})
    List<Evaluation> findByGroupIdIn(List<Long> groupIds);

    @EntityGraph(attributePaths = {"topic", "lecturer", "lecturer.user", "group"})
    Optional<Evaluation> findByTopicIdAndGroupIdAndLecturerIdAndEvaluationType(
            Long topicId, Long groupId, Long lecturerId, String evaluationType);

}
