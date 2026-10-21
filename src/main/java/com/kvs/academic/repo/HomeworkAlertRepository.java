package com.kvs.academic.repo;

import com.kvs.academic.entity.HomeworkAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface HomeworkAlertRepository extends JpaRepository<HomeworkAlert, UUID> {
    boolean existsByHomeworkIdAndStudentId(UUID homeworkId, UUID studentId);
    List<HomeworkAlert> findByStudentIdOrderBySentAtDesc(UUID studentId);
    List<HomeworkAlert> findByHomeworkIdIn(Collection<UUID> homeworkIds);
}
