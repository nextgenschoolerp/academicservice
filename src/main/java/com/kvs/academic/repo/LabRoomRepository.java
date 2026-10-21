package com.kvs.academic.repo;

import com.kvs.academic.entity.LabRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabRoomRepository extends JpaRepository<LabRoom, UUID> {
    List<LabRoom> findByTenantIdOrderByRoomNameAsc(String tenantId);
    boolean existsByTenantIdAndBranchIdAndRoomNameIgnoreCase(String tenantId, String branchId, String roomName);
}
