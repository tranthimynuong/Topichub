package topicmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import topicmanagement.entity.AppUser;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.isActive = true")
    long countActiveUsers();
    
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.role.code = :roleCode")
    long countByRoleCode(@Param("roleCode") String roleCode);

    @Query("SELECT u FROM AppUser u WHERE u.role.code = :roleCode")
    java.util.List<AppUser> findByRoleCode(@Param("roleCode") String roleCode);

    @Query("SELECT u FROM AppUser u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :kw, '%'))")
    java.util.List<AppUser> searchUsers(@Param("kw") String keyword);
}
