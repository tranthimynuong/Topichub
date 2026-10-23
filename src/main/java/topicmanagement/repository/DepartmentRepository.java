package topicmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import topicmanagement.entity.Department;
import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByIsActiveTrue();
    Optional<Department> findByCode(String code);
    Optional<Department> findByCodeIgnoreCase(String code);
}
