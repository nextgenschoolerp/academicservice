package com.kvs.academic.repo;

import com.kvs.academic.entity.HomeworkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {
    List<HomeworkSubmission> findByHomeworkId(UUID homeworkId);
    Optional<HomeworkSubmission> findByHomeworkIdAndStudentId(UUID homeworkId, UUID studentId);
    List<HomeworkSubmission> findByStudentIdIn(Collection<UUID> studentIds);
    List<HomeworkSubmission> findByHomeworkIdIn(Collection<UUID> homeworkIds);
}
