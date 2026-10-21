package com.kvs.academic.repo;

import com.kvs.academic.entity.LessonPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonPlanRepository extends JpaRepository<LessonPlan, UUID> {
    List<LessonPlan> findByTenantIdAndPlanDateBetweenOrderByPlanDateAsc(String tenantId, LocalDate from, LocalDate to);
    List<LessonPlan> findByTenantIdAndAcademicYearIdAndArchivedFalse(String tenantId, UUID academicYearId);
    List<LessonPlan> findByTenantIdAndArchivedTrueOrderByPlanDateDesc(String tenantId);
    Optional<LessonPlan> findByTenantIdAndTeacherIdAndSectionIdAndSubjectIdAndPlanDate(String tenantId, String teacherId, UUID sectionId, UUID subjectId, LocalDate planDate);
}
