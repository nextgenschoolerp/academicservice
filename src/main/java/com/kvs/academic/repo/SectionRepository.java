package com.kvs.academic.repo;

import com.kvs.academic.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SectionRepository extends JpaRepository<Section, UUID> {
    List<Section> findByGradeId(UUID gradeId);
    List<Section> findByGradeIdIn(Collection<UUID> gradeIds);
    Optional<Section> findByGradeIdAndSectionName(UUID gradeId, String sectionName);
}
