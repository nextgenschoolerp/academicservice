package com.kvs.academic.repo;

import com.kvs.academic.entity.HomeworkAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface HomeworkAssignmentRepository extends JpaRepository<HomeworkAssignment, UUID> {
    List<HomeworkAssignment> findBySectionIdOrderByDueAtDesc(UUID sectionId);
    List<HomeworkAssignment> findByTenantIdAndDueAtBefore(String tenantId, Instant dueAt);
    List<HomeworkAssignment> findBySectionIdAndDueAtBefore(UUID sectionId, Instant dueAt);
    List<HomeworkAssignment> findBySectionIdIn(Collection<UUID> sectionIds);
}
