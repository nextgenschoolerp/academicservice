package com.kvs.academic.repo;

import com.kvs.academic.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {
    List<Quiz> findByTenantIdAndGradeIdOrderByCreatedAtDesc(String tenantId, UUID gradeId);
    List<Quiz> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
