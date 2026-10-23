package topicmanagement.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import topicmanagement.entity.Announcement;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    @Query(value = "SELECT * FROM announcements WHERE id = :id FOR UPDATE", nativeQuery = true)
    java.util.Optional<Announcement> findForUpdate(@Param("id") Long id);
    @Modifying
    @Query(value = "INSERT INTO announcements (event_key, department_id, period_id, title, content, category, is_pinned, created_at) "
            + "VALUES (:eventKey, :departmentId, :periodId, :title, :content, 'PERIOD', false, :createdAt) "
            + "ON CONFLICT (event_key) DO NOTHING", nativeQuery = true)
    int insertDeadlineOnce(@Param("eventKey") String eventKey, @Param("departmentId") Long departmentId,
            @Param("periodId") Long periodId, @Param("title") String title,
            @Param("content") String content, @Param("createdAt") OffsetDateTime createdAt);

    @EntityGraph(attributePaths = {"department", "period", "author"})
    List<Announcement> findByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"department", "period", "author"})
    List<Announcement> findByPeriodId(Long periodId);

    @EntityGraph(attributePaths = {"department", "period", "author"})
    List<Announcement> findAllByOrderByIsPinnedDescCreatedAtDesc();

    @EntityGraph(attributePaths = {"department", "period", "author"})
    @org.springframework.data.jpa.repository.Query("SELECT a FROM Announcement a WHERE a.department IS NULL OR a.department.id = :departmentId ORDER BY a.isPinned DESC, a.createdAt DESC")
    List<Announcement> findByDepartmentIdOrGlobalOrderByIsPinnedDescCreatedAtDesc(@org.springframework.data.repository.query.Param("departmentId") Long departmentId);
}
