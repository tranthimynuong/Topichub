package topicmanagement.repository;

import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import topicmanagement.entity.Submission;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    @Override
    @NonNull
    Optional<Submission> findById(@NonNull Long id);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    @Override
    @NonNull
    List<Submission> findAll();

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    @Override
    @NonNull
    Page<Submission> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    @Query("SELECT s FROM Submission s WHERE s.submittedByStudent.id = :studentId")
    List<Submission> findByStudentId(@Param("studentId") Long studentId);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    List<Submission> findByGroupId(Long groupId);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    Page<Submission> findByGroupId(Long groupId, Pageable pageable);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    List<Submission> findByTopicSupervisorId(Long supervisorId);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    Page<Submission> findByTopicSupervisorId(Long supervisorId, Pageable pageable);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    List<Submission> findByTopicId(Long topicId);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    @Query("SELECT s FROM Submission s WHERE s.submittedByStudent.id = :studentId AND s.topic.id = :topicId")
    List<Submission> findByStudentIdAndTopicId(@Param("studentId") Long studentId, @Param("topicId") Long topicId);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    @Query("SELECT s FROM Submission s WHERE s.topic.supervisor.id = :lecturerId OR (s.topic.coSupervisor IS NOT NULL AND s.topic.coSupervisor.id = :lecturerId)")
    List<Submission> findBySupervisorOrCoSupervisorId(@Param("lecturerId") Long lecturerId);

    @EntityGraph(attributePaths = {"topic", "group", "submittedByStudent", "submittedByStudent.user"})
    @Query("SELECT s FROM Submission s WHERE s.topic.supervisor.id = :lecturerId OR (s.topic.coSupervisor IS NOT NULL AND s.topic.coSupervisor.id = :lecturerId)")
    Page<Submission> findBySupervisorOrCoSupervisorId(@Param("lecturerId") Long lecturerId, Pageable pageable);
}
