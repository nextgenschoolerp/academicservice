package com.kvs.academic.repo;

import com.kvs.academic.entity.SyllabusTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SyllabusTopicRepository extends JpaRepository<SyllabusTopic, UUID> {
    List<SyllabusTopic> findByTenantIdAndAcademicYearIdAndGradeIdAndSubjectIdOrderBySequenceNoAsc(String tenantId, UUID academicYearId, UUID gradeId, UUID subjectId);
    List<SyllabusTopic> findByTenantIdAndAcademicYearIdAndGradeIdOrderBySubjectIdAscSequenceNoAsc(String tenantId, UUID academicYearId, UUID gradeId);
    List<SyllabusTopic> findByTenantIdAndAcademicYearId(String tenantId, UUID academicYearId);
    List<SyllabusTopic> findByTenantIdAndArchivedTrueOrderByAcademicYearIdAscGradeIdAscSubjectIdAscSequenceNoAsc(String tenantId);
}
