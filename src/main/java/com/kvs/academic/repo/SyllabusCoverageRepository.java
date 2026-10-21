package com.kvs.academic.repo;

import com.kvs.academic.entity.SyllabusCoverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyllabusCoverageRepository extends JpaRepository<SyllabusCoverage, UUID> {
    List<SyllabusCoverage> findBySectionId(UUID sectionId);
    Optional<SyllabusCoverage> findByTopicIdAndSectionId(UUID topicId, UUID sectionId);
    boolean existsByTopicIdIn(Collection<UUID> topicIds);
}
