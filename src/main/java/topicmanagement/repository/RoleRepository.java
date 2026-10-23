package topicmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import topicmanagement.entity.Role;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}
