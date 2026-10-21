package com.kvs.academic.repo;

import com.kvs.academic.entity.RemedialSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RemedialSessionRepository extends JpaRepository<RemedialSession, UUID> {
    List<RemedialSession> findByTenantIdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(String tenantId, LocalDate from, LocalDate to);
    List<RemedialSession> findByTenantIdAndTeacherIdAndSessionDate(String tenantId, String teacherId, LocalDate date);
    List<RemedialSession> findByTenantIdOrderBySessionDateDesc(String tenantId);
}
