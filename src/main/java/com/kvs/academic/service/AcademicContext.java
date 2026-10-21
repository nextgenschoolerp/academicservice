package com.kvs.academic.service;

import com.kvs.academic.entity.AcademicSession;
import com.kvs.academic.entity.FacultyWorkload;
import com.kvs.academic.entity.Grade;
import com.kvs.academic.entity.Section;
import com.kvs.academic.entity.StudentMaster;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.enums.StudentStatus;
import com.kvs.academic.repo.AcademicSessionRepository;
import com.kvs.academic.repo.FacultyWorkloadRepository;
import com.kvs.academic.repo.GradeRepository;
import com.kvs.academic.repo.SectionRepository;
import com.kvs.academic.repo.StudentMasterRepository;
import com.kvs.academic.repo.SubjectRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Request context shared by the Module 06 services: the caller's institution and identity, tenant-checked lookups of
 * studentservice's read model (students, classes, sections, academic years) and of the subject catalog, and display names.
 * Teachers are identified by their authservice user id (the token's {@code userId} claim).
 */
@Component
public class AcademicContext {

    /** Students who are on a section's roll (PROMOTED students stay listed until their next class starts). */
    private static final Set<StudentStatus> ON_ROLL = Set.of(StudentStatus.ACTIVE, StudentStatus.PROMOTED);

    private static final List<String> STAFF_ROLES = List.of("ACADEMIC_COORDINATOR", "TIMETABLE_INCHARGE", "SUBSTITUTION_INCHARGE",
            "ELEARNING_ADMIN", "SUBJECT_TEACHER", "CLASS_TEACHER", "SCHOOL_PRINCIPAL", "VICE_PRINCIPAL", "SUPER_ADMIN");

    @Autowired private TenantAccessGuard guard;
    @Autowired private StudentMasterRepository studentRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private FacultyWorkloadRepository workloadRepository;
    @Autowired private AcademicSessionRepository sessionRepository;

    // ------------------------------------------------------------------ caller

    /**
     * The institution a request acts on: the one named in the request (only a SUPER_ADMIN may name another), else the token's.
     */
    public String tenant(String requested) {
        if (requested != null && !requested.isBlank()) {
            guard.requireTenant(requested);
            return requested;
        }
        return guard.currentTenantId()
                .orElseThrow(() -> new BadRequestException("tenantId is required: the token carries no institution"));
    }

    /** Checks that a record belongs to the caller's institution; another institution's record is reported as not found. */
    public void requireTenant(String tenantId, String resource, String field, Object value) {
        guard.requireTenantRecord(tenantId, resource, field, value);
    }

    /** The caller's e-mail (or username) for audit fields. */
    public String actor() {
        return SecurityUtils.getCurrentUserEmail().orElse("system");
    }

    /** The caller's authservice user id; teachers are identified by it. */
    public String userId() {
        return guard.currentUserId().orElse(actor());
    }

    public boolean hasRole(String... roles) {
        List<String> granted = SecurityUtils.getCurrentUserRoles();
        for (String role : roles) {
            if (granted.contains("ROLE_" + role) || granted.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /** True for a parent account without any staff role: such callers only see their own children. */
    public boolean isParentOnly() {
        return hasRole("APPLICANT_PARENT") && !hasRole(STAFF_ROLES.toArray(String[]::new));
    }

    // ------------------------------------------------------------------ students, classes, subjects

    /** A student of the caller's institution; a parent may only open their own children. */
    public StudentMaster student(UUID studentId) {
        StudentMaster student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "studentId", studentId));
        guard.requireTenantRecord(student.getTenantId(), "Student", "studentId", studentId);
        if (isParentOnly() && !userId().equals(student.getParentAccountId())) {
            throw new ForbiddenException("You may only access your own children's records");
        }
        return student;
    }

    /** The children linked to the calling parent's account. */
    public List<StudentMaster> ownChildren() {
        return studentRepository.findByParentAccountId(userId());
    }

    public Section section(UUID sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", "sectionId", sectionId));
        Grade grade = gradeRepository.findById(section.getGradeId())
                .orElseThrow(() -> new ResourceNotFoundException("Section", "sectionId", sectionId));
        guard.requireTenantRecord(grade.getTenantId(), "Section", "sectionId", sectionId);
        return section;
    }

    public Grade grade(UUID gradeId) {
        Grade grade = gradeRepository.findById(gradeId).orElseThrow(() -> new ResourceNotFoundException("Grade", "gradeId", gradeId));
        guard.requireTenantRecord(grade.getTenantId(), "Grade", "gradeId", gradeId);
        return grade;
    }

    public Subject subject(UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "subjectId", subjectId));
        guard.requireTenantRecord(subject.getTenantId(), "Subject", "subjectId", subjectId);
        return subject;
    }

    /** The tenant a section belongs to (through its class). */
    public String tenantOf(Section section) {
        return gradeRepository.findById(section.getGradeId()).map(Grade::getTenantId).orElse(null);
    }

    /** "Class 7-A" */
    public String className(Section section) {
        if (section == null) {
            return null;
        }
        String grade = gradeRepository.findById(section.getGradeId()).map(Grade::getGradeName).orElse("Class");
        return grade + "-" + section.getSectionName();
    }

    public String className(UUID sectionId) {
        return sectionId == null ? null : sectionRepository.findById(sectionId).map(this::className).orElse(null);
    }

    /** Students on the section's roll, by roll number. */
    public List<StudentMaster> roster(UUID sectionId) {
        return studentRepository.findBySectionIdOrderByRollNumberAsc(sectionId).stream()
                .filter(s -> ON_ROLL.contains(s.getStudentStatus()))
                .sorted(Comparator.comparing(StudentMaster::getRollNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public static String studentName(StudentMaster student) {
        return student.getFirstName() + " " + student.getLastName();
    }

    public Map<UUID, Subject> subjects(String tenantId) {
        return subjectRepository.findByTenantIdOrderBySubjectCodeAsc(tenantId).stream()
                .collect(Collectors.toMap(Subject::getSubjectId, s -> s));
    }

    // ------------------------------------------------------------------ teachers and academic years

    /** teacherId → name from the faculty workload register (US-06001). */
    public Map<String, String> teacherNames(String tenantId) {
        return workloadRepository.findByTenantIdOrderByTeacherNameAsc(tenantId).stream()
                .collect(Collectors.toMap(FacultyWorkload::getTeacherId, FacultyWorkload::getTeacherName, (a, b) -> a));
    }

    public String teacherName(String tenantId, String teacherId) {
        if (teacherId == null) {
            return null;
        }
        return workloadRepository.findByTenantIdAndTeacherId(tenantId, teacherId).map(FacultyWorkload::getTeacherName).orElse(teacherId);
    }

    /** The academic year that contains the date, else the institution's active year. */
    public AcademicSession academicYear(String tenantId, LocalDate date) {
        List<AcademicSession> sessions = sessionRepository.findByTenantId(tenantId);
        return sessions.stream()
                .filter(s -> !date.isBefore(s.getStartDate()) && !date.isAfter(s.getEndDate()))
                .findFirst()
                .or(() -> sessions.stream().filter(s -> Boolean.TRUE.equals(s.getIsActive())).findFirst())
                .orElseThrow(() -> new BadRequestException("No academic year is set up for " + date));
    }

    public AcademicSession academicYearById(String tenantId, UUID academicYearId) {
        AcademicSession session = sessionRepository.findById(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", "academicYearId", academicYearId));
        if (!session.getTenantId().equalsIgnoreCase(tenantId)) {
            throw new ResourceNotFoundException("Academic year", "academicYearId", academicYearId);
        }
        return session;
    }
}
