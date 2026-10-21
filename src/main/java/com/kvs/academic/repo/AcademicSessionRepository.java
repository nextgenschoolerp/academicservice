package com.kvs.academic.repo;

import com.kvs.academic.entity.AcademicSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcademicSessionRepository extends JpaRepository<AcademicSession, UUID> {
    List<AcademicSession> findByTenantId(String tenantId);
    Optional<AcademicSession> findByTenantIdAndIsActiveTrue(String tenantId);
    boolean existsByTenantIdAndSessionName(String tenantId, String sessionName);
}
