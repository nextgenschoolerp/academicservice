package com.kvs.academic.service;

import com.kvs.academic.dto.*;
import com.kvs.academic.entity.AcademicSession;
import com.kvs.academic.entity.FacultyAbsence;
import com.kvs.academic.entity.FacultyWorkload;
import com.kvs.academic.entity.LessonPlan;
import com.kvs.academic.entity.Section;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.entity.SyllabusTopic;
import com.kvs.academic.entity.TimetableEntry;
import com.kvs.academic.enums.LessonPlanStatus;
import com.kvs.academic.enums.ReviewDecision;
import com.kvs.academic.repo.FacultyAbsenceRepository;
import com.kvs.academic.repo.FacultyWorkloadRepository;
import com.kvs.academic.repo.LessonPlanRepository;
import com.kvs.academic.repo.SyllabusTopicRepository;
import com.kvs.academic.repo.TimetableEntryRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Daily lesson plans and principal approval (US-06013), the submission compliance report, and the year-end archive of
 * lesson plans and syllabus plans for faculty reference (US-06019). A teacher writes and submits only their own plans;
 * a reviewer cannot approve their own plan; archived records are read-only.
 */
@Service
@Transactional
public class LessonPlanService {

    static final int MAX_REPORT_DAYS = 92;

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private LessonPlanRepository planRepository;
    @Autowired private SyllabusTopicRepository topicRepository;
    @Autowired private TimetableEntryRepository entryRepository;
    @Autowired private FacultyWorkloadRepository workloadRepository;
    @Autowired private FacultyAbsenceRepository absenceRepository;

    public LessonPlanResponse create(LessonPlanRequest request) {
        Section section = ctx.section(request.getSectionId());
        Subject subject = ctx.subject(request.getSubjectId());
        String tenant = subject.getTenantId();
        String teacherId = ctx.userId();
        AcademicSession year = ctx.academicYear(tenant, request.getPlanDate());
        validateTopic(request.getTopicId(), section, subject);
        if (planRepository.findByTenantIdAndTeacherIdAndSectionIdAndSubjectIdAndPlanDate(tenant, teacherId, section.getSectionId(),
                subject.getSubjectId(), request.getPlanDate()).isPresent()) {
            throw new ConflictException("You already have a " + subject.getSubjectCode() + " plan for " + ctx.className(section) + " on "
                    + request.getPlanDate(), "DUPLICATE_LESSON_PLAN");
        }
        LessonPlan plan = LessonPlan.builder().tenantId(tenant).academicYearId(year.getAcademicYearId()).teacherId(teacherId)
                .gradeId(section.getGradeId()).sectionId(section.getSectionId()).subjectId(subject.getSubjectId()).planDate(request.getPlanDate())
                .build();
        apply(plan, request);
        if (Boolean.TRUE.equals(request.getSubmit())) {
            plan.setStatus(LessonPlanStatus.SUBMITTED);
            plan.setSubmittedAt(Instant.now());
        }
        LessonPlan saved = planRepository.save(plan);
        audit.logSuccess("LESSON_PLAN", saved.getStatus() == LessonPlanStatus.SUBMITTED ? "LESSON_PLAN_SUBMITTED" : "LESSON_PLAN_DRAFTED",
                ctx.className(section) + " " + subject.getSubjectCode() + " " + request.getPlanDate());
        return toResponse(saved);
    }

    public LessonPlanResponse update(UUID planId, LessonPlanRequest request) {
        LessonPlan plan = ownEditable(planId);
        Section section = ctx.section(request.getSectionId());
        Subject subject = ctx.subject(request.getSubjectId());
        validateTopic(request.getTopicId(), section, subject);
        plan.setSectionId(section.getSectionId());
        plan.setGradeId(section.getGradeId());
        plan.setSubjectId(subject.getSubjectId());
        plan.setPlanDate(request.getPlanDate());
        apply(plan, request);
        if (Boolean.TRUE.equals(request.getSubmit())) {
            plan.setStatus(LessonPlanStatus.SUBMITTED);
            plan.setSubmittedAt(Instant.now());
        }
        audit.logSuccess("LESSON_PLAN", "LESSON_PLAN_UPDATED", planId.toString());
        return toResponse(planRepository.save(plan));
    }

    public LessonPlanResponse submit(UUID planId) {
        LessonPlan plan = ownEditable(planId);
        plan.setStatus(LessonPlanStatus.SUBMITTED);
        plan.setSubmittedAt(Instant.now());
        audit.logSuccess("LESSON_PLAN", "LESSON_PLAN_SUBMITTED", planId.toString());
        return toResponse(planRepository.save(plan));
    }

    public LessonPlanResponse review(UUID planId, ReviewLessonPlanRequest request) {
        LessonPlan plan = plan(planId);
        requireNotArchived(plan);
        if (plan.getStatus() != LessonPlanStatus.SUBMITTED) {
            throw new ConflictException("Only a submitted plan can be reviewed; this one is " + plan.getStatus(), "INVALID_STATUS");
        }
        if (plan.getTeacherId().equals(ctx.userId())) {
            throw new ForbiddenException("You cannot review your own lesson plan");
        }
        if (request.getDecision() == ReviewDecision.RETURNED && (request.getRemarks() == null || request.getRemarks().isBlank())) {
            throw new BadRequestException("remarks are required when a plan is returned");
        }
        plan.setStatus(request.getDecision() == ReviewDecision.APPROVED ? LessonPlanStatus.APPROVED : LessonPlanStatus.RETURNED);
        plan.setReviewedBy(ctx.actor());
        plan.setReviewedAt(Instant.now());
        plan.setReviewRemarks(request.getRemarks());
        audit.logSuccess("LESSON_PLAN", "LESSON_PLAN_" + plan.getStatus(), planId + " by " + ctx.actor());
        return toResponse(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public List<LessonPlanResponse> list(String teacherId, LessonPlanStatus status, UUID sectionId, LocalDate from, LocalDate to) {
        String tenant = ctx.tenant(null);
        LocalDate end = to == null ? LocalDate.now().plusDays(14) : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        // A subject or class teacher without a reviewer role sees only their own plans
        String onlyTeacher = ctx.hasRole("SCHOOL_PRINCIPAL", "VICE_PRINCIPAL", "ACADEMIC_COORDINATOR", "ELEARNING_ADMIN", "SUPER_ADMIN")
                ? teacherId : ctx.userId();
        return planRepository.findByTenantIdAndPlanDateBetweenOrderByPlanDateAsc(tenant, start, end).stream()
                .filter(p -> onlyTeacher == null || onlyTeacher.equals(p.getTeacherId()))
                .filter(p -> status == null || status == p.getStatus())
                .filter(p -> sectionId == null || sectionId.equals(p.getSectionId()))
                .map(this::toResponse).toList();
    }

    /**
     * Per teacher: days in the range on which their timetable has periods (minus days they were absent), how many of those
     * days have a submitted plan, and how many plans are approved, waiting or returned.
     */
    @Transactional(readOnly = true)
    public LessonPlanComplianceResponse compliance(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_REPORT_DAYS) {
            throw new BadRequestException("The report covers 1 to " + MAX_REPORT_DAYS + " days, from must not be after to");
        }
        String tenant = ctx.tenant(null);
        Map<String, Set<Integer>> teachingDays = entryRepository.findByTenantId(tenant).stream()
                .collect(Collectors.groupingBy(TimetableEntry::getTeacherId, Collectors.mapping(TimetableEntry::getDayOfWeek, Collectors.toSet())));
        Set<String> absences = new HashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (FacultyAbsence a : absenceRepository.findByTenantIdAndAbsenceDate(tenant, d)) {
                absences.add(a.getTeacherId() + "|" + d);
            }
        }
        List<LessonPlan> plans = planRepository.findByTenantIdAndPlanDateBetweenOrderByPlanDateAsc(tenant, from, to);
        List<TeacherCompliance> rows = new ArrayList<>();
        for (FacultyWorkload teacher : workloadRepository.findByTenantIdOrderByTeacherNameAsc(tenant)) {
            Set<Integer> weekdays = teachingDays.getOrDefault(teacher.getTeacherId(), Set.of());
            Set<LocalDate> expected = new TreeSet<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (weekdays.contains(d.getDayOfWeek().getValue()) && !absences.contains(teacher.getTeacherId() + "|" + d)) {
                    expected.add(d);
                }
            }
            List<LessonPlan> own = plans.stream().filter(p -> p.getTeacherId().equals(teacher.getTeacherId())
                    && p.getStatus() != LessonPlanStatus.DRAFT).toList();
            Set<LocalDate> planned = own.stream().map(LessonPlan::getPlanDate).collect(Collectors.toSet());
            int missing = (int) expected.stream().filter(d -> !planned.contains(d)).count();
            rows.add(TeacherCompliance.builder().teacherId(teacher.getTeacherId()).teacherName(teacher.getTeacherName())
                    .teachingDays(expected.size()).plansSubmitted(own.size())
                    .approved(count(own, LessonPlanStatus.APPROVED)).pendingReview(count(own, LessonPlanStatus.SUBMITTED))
                    .returned(count(own, LessonPlanStatus.RETURNED)).missingDays(missing)
                    .compliancePercent(SchedulingService.percent(expected.size() - missing, expected.size())).build());
        }
        rows.sort(Comparator.comparing(TeacherCompliance::getCompliancePercent).thenComparing(TeacherCompliance::getTeacherName));
        return LessonPlanComplianceResponse.builder().from(from).to(to).teachers(rows).build();
    }

    private static int count(List<LessonPlan> plans, LessonPlanStatus status) {
        return (int) plans.stream().filter(p -> p.getStatus() == status).count();
    }

    // ------------------------------------------------------------------ US-06019 archive

    /** Archives a finished academic year's lesson plans and syllabus plans; they stay searchable but can no longer change. */
    public ArchiveResultResponse archiveYear(ArchiveYearRequest request) {
        String tenant = ctx.tenant(request.getTenantId());
        AcademicSession year = ctx.academicYearById(tenant, request.getAcademicYearId());
        if (!year.getEndDate().isBefore(LocalDate.now())) {
            throw new ConflictException(year.getSessionName() + " ends on " + year.getEndDate() + "; only a closed academic year can be archived",
                    "YEAR_NOT_CLOSED");
        }
        Instant now = Instant.now();
        List<LessonPlan> plans = planRepository.findByTenantIdAndAcademicYearIdAndArchivedFalse(tenant, year.getAcademicYearId());
        for (LessonPlan plan : plans) {
            plan.setArchived(true);
            plan.setArchivedAt(now);
        }
        planRepository.saveAll(plans);
        List<SyllabusTopic> topics = topicRepository.findByTenantIdAndAcademicYearId(tenant, year.getAcademicYearId()).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getArchived())).toList();
        for (SyllabusTopic topic : topics) {
            topic.setArchived(true);
            topic.setArchivedAt(now);
        }
        topicRepository.saveAll(topics);
        audit.logSuccess("ACADEMIC_ARCHIVE", "ACADEMIC_YEAR_ARCHIVED", year.getSessionName() + ": " + plans.size() + " lesson plans, "
                + topics.size() + " syllabus chapters");
        return ArchiveResultResponse.builder().academicYearId(year.getAcademicYearId()).sessionName(year.getSessionName())
                .lessonPlansArchived(plans.size()).syllabusTopicsArchived(topics.size()).archivedAt(now).build();
    }

    @Transactional(readOnly = true)
    public List<LessonPlanResponse> archivedPlans(UUID academicYearId, UUID gradeId, UUID subjectId, String query) {
        String tenant = ctx.tenant(null);
        String q = query == null ? null : query.trim().toLowerCase(Locale.ROOT);
        Map<UUID, String> chapters = new HashMap<>();
        return planRepository.findByTenantIdAndArchivedTrueOrderByPlanDateDesc(tenant).stream()
                .filter(p -> academicYearId == null || academicYearId.equals(p.getAcademicYearId()))
                .filter(p -> gradeId == null || gradeId.equals(p.getGradeId()))
                .filter(p -> subjectId == null || subjectId.equals(p.getSubjectId()))
                .filter(p -> q == null || q.isEmpty() || contains(p.getObjectives(), q) || contains(p.getActivities(), q)
                        || contains(p.getAssessment(), q) || contains(chapter(p.getTopicId(), chapters), q))
                .limit(200).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SyllabusPlanResponse> archivedSyllabus(UUID academicYearId, UUID gradeId, UUID subjectId) {
        String tenant = ctx.tenant(null);
        Map<UUID, Subject> subjects = ctx.subjects(tenant);
        LocalDate today = LocalDate.now();
        return topicRepository.findByTenantIdAndArchivedTrueOrderByAcademicYearIdAscGradeIdAscSubjectIdAscSequenceNoAsc(tenant).stream()
                .filter(t -> academicYearId == null || academicYearId.equals(t.getAcademicYearId()))
                .filter(t -> gradeId == null || gradeId.equals(t.getGradeId()))
                .filter(t -> subjectId == null || subjectId.equals(t.getSubjectId()))
                .collect(Collectors.groupingBy(t -> t.getAcademicYearId() + "|" + t.getGradeId() + "|" + t.getSubjectId(), LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream().map(topics -> {
                    SyllabusTopic first = topics.get(0);
                    Subject subject = subjects.get(first.getSubjectId());
                    var grade = ctx.grade(first.getGradeId());
                    return SyllabusPlanResponse.builder().academicYearId(first.getAcademicYearId()).gradeId(grade.getGradeId())
                            .gradeName(grade.getGradeName()).subjectId(first.getSubjectId())
                            .subjectCode(subject == null ? null : subject.getSubjectCode()).subjectName(subject == null ? null : subject.getSubjectName())
                            .topics(topics.stream().map(t -> SyllabusService.toTopic(t, null, today)).toList()).build();
                }).toList();
    }

    private static boolean contains(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    private String chapter(UUID topicId, Map<UUID, String> cache) {
        if (topicId == null) {
            return null;
        }
        return cache.computeIfAbsent(topicId, id -> topicRepository.findById(id).map(SyllabusTopic::getChapterTitle).orElse(""));
    }

    // ------------------------------------------------------------------ helpers

    private LessonPlan plan(UUID planId) {
        LessonPlan plan = planRepository.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Lesson plan", "lessonPlanId", planId));
        ctx.requireTenant(plan.getTenantId(), "Lesson plan", "lessonPlanId", planId);
        return plan;
    }

    private LessonPlan ownEditable(UUID planId) {
        LessonPlan plan = plan(planId);
        requireNotArchived(plan);
        if (!plan.getTeacherId().equals(ctx.userId()) && !ctx.hasRole("SUPER_ADMIN")) {
            throw new ForbiddenException("Only the teacher who wrote this plan can change it");
        }
        if (plan.getStatus() != LessonPlanStatus.DRAFT && plan.getStatus() != LessonPlanStatus.RETURNED) {
            throw new ConflictException("A " + plan.getStatus().name().toLowerCase(Locale.ROOT) + " plan can no longer be changed", "INVALID_STATUS");
        }
        return plan;
    }

    private static void requireNotArchived(LessonPlan plan) {
        if (Boolean.TRUE.equals(plan.getArchived())) {
            throw new ConflictException("This lesson plan is archived and read-only", "ARCHIVED");
        }
    }

    private void validateTopic(UUID topicId, Section section, Subject subject) {
        if (topicId == null) {
            return;
        }
        SyllabusTopic topic = topicRepository.findById(topicId).orElseThrow(() -> new ResourceNotFoundException("Syllabus topic", "topicId", topicId));
        if (!topic.getGradeId().equals(section.getGradeId()) || !topic.getSubjectId().equals(subject.getSubjectId())) {
            throw new BadRequestException("The chapter does not belong to " + subject.getSubjectCode() + " of " + ctx.className(section));
        }
    }

    private static void apply(LessonPlan plan, LessonPlanRequest request) {
        plan.setTopicId(request.getTopicId());
        plan.setObjectives(request.getObjectives().trim());
        plan.setActivities(request.getActivities());
        plan.setAssessment(request.getAssessment());
    }

    private LessonPlanResponse toResponse(LessonPlan p) {
        Subject subject = ctx.subjects(p.getTenantId()).get(p.getSubjectId());
        String chapter = p.getTopicId() == null ? null : topicRepository.findById(p.getTopicId()).map(SyllabusTopic::getChapterTitle).orElse(null);
        return LessonPlanResponse.builder().lessonPlanId(p.getLessonPlanId()).academicYearId(p.getAcademicYearId()).teacherId(p.getTeacherId())
                .teacherName(ctx.teacherName(p.getTenantId(), p.getTeacherId())).sectionId(p.getSectionId()).className(ctx.className(p.getSectionId()))
                .subjectId(p.getSubjectId()).subjectCode(subject == null ? null : subject.getSubjectCode()).planDate(p.getPlanDate())
                .topicId(p.getTopicId()).chapterTitle(chapter).objectives(p.getObjectives()).activities(p.getActivities())
                .assessment(p.getAssessment()).status(p.getStatus()).submittedAt(p.getSubmittedAt()).reviewedBy(p.getReviewedBy())
                .reviewedAt(p.getReviewedAt()).reviewRemarks(p.getReviewRemarks()).archived(p.getArchived()).build();
    }
}
