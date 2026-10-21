package com.kvs.academic.repo;

import com.kvs.academic.entity.LearningAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LearningAssetRepository extends JpaRepository<LearningAsset, UUID> {
    List<LearningAsset> findByTenantIdAndGradeIdOrderByCreatedAtDesc(String tenantId, UUID gradeId);
}
