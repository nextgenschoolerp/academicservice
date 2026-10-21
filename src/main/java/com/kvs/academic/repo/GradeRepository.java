package com.kvs.academic.repo;

import com.kvs.academic.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradeRepository extends JpaRepository<Grade, UUID> {
    List<Grade> findByTenantIdOrderBySequenceOrderAsc(String tenantId);
    Optional<Grade> findByTenantIdAndGradeCode(String tenantId, String gradeCode);
    Optional<Grade> findByTenantIdAndSequenceOrder(String tenantId, Integer sequenceOrder);
    boolean existsByTenantIdAndGradeCode(String tenantId, String gradeCode);
}
