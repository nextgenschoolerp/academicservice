package com.kvs.academic.repo;

import com.kvs.academic.entity.BellSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BellScheduleRepository extends JpaRepository<BellSchedule, UUID> {
    List<BellSchedule> findByTenantIdOrderByScheduleNameAsc(String tenantId);
    List<BellSchedule> findByTenantIdAndBranchIdOrderByScheduleNameAsc(String tenantId, String branchId);
    List<BellSchedule> findByTenantIdAndBranchIdAndActiveTrue(String tenantId, String branchId);
}
