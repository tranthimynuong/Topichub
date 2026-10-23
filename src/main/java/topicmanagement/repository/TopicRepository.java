package topicmanagement.repository;

import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import topicmanagement.entity.Topic;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    @Query(value = "SELECT * FROM topics WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Topic> findForPublication(@Param("id") Long id);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    @Override
    @NonNull
    Optional<Topic> findById(@NonNull Long id);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    @Override
    @NonNull
    List<Topic> findAll();

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    @Override
    @NonNull
    Page<Topic> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findBySupervisorId(Long supervisorId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByReviewerId(Long reviewerId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByCouncilId(Long councilId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByCouncilIdIn(List<Long> councilIds);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByPeriodId(Long periodId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByStatus(String status);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    Page<Topic> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    Page<Topic> findByDepartmentId(Long departmentId, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByDepartmentIdAndStatus(Long departmentId, String status);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    Page<Topic> findByDepartmentIdAndStatus(Long departmentId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    List<Topic> findByDepartmentIdAndPeriodId(Long departmentId, Long periodId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    @Query("SELECT t FROM Topic t WHERE t.supervisor.id = :lecturerId OR (t.coSupervisor IS NOT NULL AND t.coSupervisor.id = :lecturerId)")
    List<Topic> findBySupervisorOrCoSupervisorId(@Param("lecturerId") Long lecturerId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    @Query("SELECT t FROM Topic t WHERE t.period.id = :periodId AND t.status = 'APPROVED'")
    List<Topic> findApprovedByPeriodId(@Param("periodId") Long periodId);

    @EntityGraph(attributePaths = {"department", "supervisor", "supervisor.user", "coSupervisor", "coSupervisor.user", "reviewer", "reviewer.user", "period", "council"})
    @Query(value = "SELECT t FROM Topic t WHERE (:departmentId IS NULL OR (t.department IS NOT NULL AND t.department.id = :departmentId) OR (t.period IS NOT NULL AND t.period.department IS NOT NULL AND t.period.department.id = :departmentId) OR (t.supervisor IS NOT NULL AND t.supervisor.department IS NOT NULL AND t.supervisor.department.id = :departmentId)) " +
           "AND (:status IS NULL OR :status = '' OR :status = 'ALL' OR t.status = :status) " +
           "AND (:periodId IS NULL OR (t.period IS NOT NULL AND t.period.id = :periodId)) " +
           "AND (:keyword IS NULL OR :keyword = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR (t.supervisor IS NOT NULL AND t.supervisor.user IS NOT NULL AND LOWER(t.supervisor.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))) " +
           "ORDER BY CASE WHEN t.status = 'PENDING' THEN 0 ELSE 1 END, t.createdAt DESC",
           countQuery = "SELECT COUNT(t) FROM Topic t WHERE (:departmentId IS NULL OR (t.department IS NOT NULL AND t.department.id = :departmentId) OR (t.period IS NOT NULL AND t.period.department IS NOT NULL AND t.period.department.id = :departmentId) OR (t.supervisor IS NOT NULL AND t.supervisor.department IS NOT NULL AND t.supervisor.department.id = :departmentId)) " +
           "AND (:status IS NULL OR :status = '' OR :status = 'ALL' OR t.status = :status) " +
           "AND (:periodId IS NULL OR (t.period IS NOT NULL AND t.period.id = :periodId)) " +
           "AND (:keyword IS NULL OR :keyword = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR (t.supervisor IS NOT NULL AND t.supervisor.user IS NOT NULL AND LOWER(t.supervisor.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))))")
    Page<Topic> findDepartmentTopicsPaged(
            @Param("departmentId") Long departmentId,
            @Param("status") String status,
            @Param("periodId") Long periodId,
            @Param("keyword") String keyword,
            Pageable pageable);

    boolean existsByCode(String code);

    long countByDepartmentId(Long departmentId);
    long countByDepartmentIdAndStatus(Long departmentId, String status);

    long countBySupervisorId(Long supervisorId);
}
