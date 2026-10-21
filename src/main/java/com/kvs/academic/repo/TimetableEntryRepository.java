package com.kvs.academic.repo;

import com.kvs.academic.entity.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, UUID> {
    List<TimetableEntry> findBySectionIdOrderByDayOfWeekAscPeriodNumberAsc(UUID sectionId);
    List<TimetableEntry> findByTenantIdAndTeacherIdOrderByDayOfWeekAscPeriodNumberAsc(String tenantId, String teacherId);
    List<TimetableEntry> findByTenantId(String tenantId);
    List<TimetableEntry> findByTenantIdAndDayOfWeek(String tenantId, Integer dayOfWeek);
    List<TimetableEntry> findBySectionIdAndDayOfWeek(UUID sectionId, Integer dayOfWeek);
    List<TimetableEntry> findBySectionIdIn(Collection<UUID> sectionIds);
    @Transactional
    void deleteBySectionIdIn(Collection<UUID> sectionIds);
}
