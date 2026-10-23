package topicmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import topicmanagement.entity.GroupMember;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    @EntityGraph(attributePaths = {"student", "student.user", "group"})
    List<GroupMember> findByGroupId(Long groupId);
    List<GroupMember> findByStudentId(Long studentId);
    Optional<GroupMember> findByGroupIdAndStudentId(Long groupId, Long studentId);
    void deleteByGroupIdAndStudentId(Long groupId, Long studentId);
    void deleteByStudentId(Long studentId);
}
