package com.kvs.academic.repo;

import com.kvs.academic.entity.FacultyAbsence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacultyAbsenceRepository extends JpaRepository<FacultyAbsence, UUID> {
    List<FacultyAbsence> findByTenantIdAndAbsenceDate(String tenantId, LocalDate absenceDate);
    Optional<FacultyAbsence> findByTenantIdAndTeacherIdAndAbsenceDate(String tenantId, String teacherId, LocalDate absenceDate);
}
