package com.kvs.academic.repo;

import com.kvs.academic.entity.ProctorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProctorEventRepository extends JpaRepository<ProctorEvent, UUID> {
    List<ProctorEvent> findByAttemptIdOrderByOccurredAtAsc(UUID attemptId);
    long countByAttemptId(UUID attemptId);
}
