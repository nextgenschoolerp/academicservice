package com.kvs.academic.repo;

import com.kvs.academic.entity.ProxySubstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProxySubstitutionRepository extends JpaRepository<ProxySubstitution, UUID> {
    List<ProxySubstitution> findByTenantIdAndSubstitutionDate(String tenantId, LocalDate substitutionDate);
    Optional<ProxySubstitution> findByTimetableIdAndSubstitutionDate(UUID timetableId, LocalDate substitutionDate);
    List<ProxySubstitution> findByTenantIdAndSubstitutionDateBetween(String tenantId, LocalDate from, LocalDate to);
    @Transactional
    long deleteByTimetableIdInAndSubstitutionDateGreaterThanEqual(Collection<UUID> timetableIds, LocalDate from);
}
