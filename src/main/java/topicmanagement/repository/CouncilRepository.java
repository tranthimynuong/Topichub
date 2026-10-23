package topicmanagement.repository;

import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import topicmanagement.entity.Council;

import java.util.List;
import java.util.Optional;

public interface CouncilRepository extends JpaRepository<Council, Long> {
    @Query(value = "SELECT * FROM councils WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Council> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    @Override
    @NonNull
    Optional<Council> findById(@NonNull Long id);

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    @Override
    @NonNull
    List<Council> findAll();

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    Optional<Council> findByCode(String code);

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    List<Council> findByPeriodId(Long periodId);

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    List<Council> findByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    Page<Council> findByDepartmentId(Long departmentId, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    List<Council> findByDepartmentIdAndPeriodId(Long departmentId, Long periodId);

    @EntityGraph(attributePaths = {"department", "period", "members", "members.lecturer", "members.lecturer.user"})
    @Query("SELECT DISTINCT c FROM Council c JOIN c.members m WHERE m.lecturer.id = :lecturerId")
    List<Council> findByLecturerId(@Param("lecturerId") Long lecturerId);

    @Query("SELECT (COUNT(c) > 0) FROM Council c WHERE (:periodId IS NULL OR (c.period IS NOT NULL AND c.period.id = :periodId)) AND LOWER(TRIM(c.name)) = LOWER(TRIM(:name))")
    boolean existsByPeriodAndNameIgnoreCase(@Param("periodId") Long periodId, @Param("name") String name);

    @Query("SELECT (COUNT(c) > 0) FROM Council c WHERE (:periodId IS NULL OR (c.period IS NOT NULL AND c.period.id = :periodId)) AND LOWER(TRIM(c.name)) = LOWER(TRIM(:name)) AND (:excludeId IS NULL OR c.id <> :excludeId)")
    boolean existsByPeriodAndNameIgnoreCaseExcludingId(@Param("periodId") Long periodId, @Param("name") String name, @Param("excludeId") Long excludeId);
}
