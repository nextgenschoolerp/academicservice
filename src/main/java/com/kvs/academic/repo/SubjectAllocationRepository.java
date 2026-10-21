package com.kvs.academic.repo;

import com.kvs.academic.entity.SubjectAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectAllocationRepository extends JpaRepository<SubjectAllocation, UUID> {
    List<SubjectAllocation> findBySectionId(UUID sectionId);
    List<SubjectAllocation> findByTenantIdAndTeacherId(String tenantId, String teacherId);
    List<SubjectAllocation> findByTenantId(String tenantId);
    Optional<SubjectAllocation> findBySectionIdAndSubjectId(UUID sectionId, UUID subjectId);
}
