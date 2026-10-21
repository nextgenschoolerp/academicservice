package com.kvs.academic.repo;

import com.kvs.academic.entity.FacultyWorkload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacultyWorkloadRepository extends JpaRepository<FacultyWorkload, UUID> {
    Optional<FacultyWorkload> findByTenantIdAndTeacherId(String tenantId, String teacherId);
    List<FacultyWorkload> findByTenantIdOrderByTeacherNameAsc(String tenantId);
}
