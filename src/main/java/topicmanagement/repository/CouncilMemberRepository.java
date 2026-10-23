package topicmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import topicmanagement.entity.CouncilMember;

import java.util.List;
import java.util.Optional;

public interface CouncilMemberRepository extends JpaRepository<CouncilMember, Long> {
    List<CouncilMember> findByCouncilId(Long councilId);
    List<CouncilMember> findByCouncilIdIn(List<Long> councilIds);
    List<CouncilMember> findByLecturerId(Long lecturerId);
    Optional<CouncilMember> findByCouncilIdAndLecturerId(Long councilId, Long lecturerId);
    void deleteByCouncilId(Long councilId);
}
