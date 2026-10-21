package com.kvs.academic.repo;

import com.kvs.academic.entity.BellPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BellPeriodRepository extends JpaRepository<BellPeriod, UUID> {
    List<BellPeriod> findByBellScheduleIdOrderBySequenceNoAsc(UUID bellScheduleId);
}
