package com.kvs.academic.repo;

import com.kvs.academic.entity.SkillBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SkillBadgeRepository extends JpaRepository<SkillBadge, UUID> {
    List<SkillBadge> findByStudentIdOrderByAwardedAtDesc(UUID studentId);
    List<SkillBadge> findByStudentIdIn(Collection<UUID> studentIds);
}
