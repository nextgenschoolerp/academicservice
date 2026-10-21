package com.kvs.academic.service;

import com.kvs.academic.dto.*;
import com.kvs.academic.entity.AcademicSession;
import com.kvs.academic.entity.Grade;
import com.kvs.academic.entity.Section;
import com.kvs.academic.entity.StudentMaster;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.entity.SyllabusCoverage;
import com.kvs.academic.entity.SyllabusTopic;
import com.kvs.academic.repo.SyllabusCoverageRepository;
import com.kvs.academic.repo.SyllabusTopicRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Annual syllabus plans and coverage tracking (US-06004): coordinators plan each class's chapters with target dates;
 * teachers mark chapters taught per section; coverage compares the share of chapters taught with the share that should
 * have been taught by the date. A section more than 10 points behind is BEHIND, more than 10 ahead is AHEAD.
 */
@Service
@Transactional
public class SyllabusService {

    static final BigDecimal TOLERANCE = BigDecimal.TEN;

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private SyllabusTopicRepository topicRepository;
    @Autowired private SyllabusCoverageRepository coverageRepository;

    /** Saves the plan of a class and subject for a year, replacing the previous one while none of its chapters is covered. */
    public SyllabusPlanResponse savePlan(SyllabusPlanRequest request) {
        String tenant = ctx.tenant(request.getTenantId());
        Grade grade = ctx.grade(request.getGradeId());
        Subject subject = ctx.subject(request.getSubjectId());
        AcademicSession year = request.getAcademicYearId() == null ? ctx.academicYear(tenant, LocalDate.now())
                : ctx.academicYearById(tenant, request.getAcademicYearId());
        LocalDate previous = null;
        for (SyllabusTopicRequest topic : request.getTopics()) {
            LocalDate date = topic.getPlannedCompletionDate();
            if (date.isBefore(year.getStartDate()) || date.isAfter(year.getEndDate())) {
                throw new BadRequestException(topic.getChapterTitle() + " is planned outside the academic year " + year.getSessionName());
            }
            if (previous != null && date.isBefore(previous)) {
                throw new BadRequestException("Chapters must be listed in teaching order: " + topic.getChapterTitle() + " is planned before the chapter above it");
            }
            previous = date;
        }
        List<SyllabusTopic> existing = topicRepository.findByTenantIdAndAcademicYearIdAndGradeIdAndSubjectIdOrderBySequenceNoAsc(
                tenant, year.getAcademicYearId(), grade.getGradeId(), subject.getSubjectId());
        if (existing.stream().anyMatch(t -> Boolean.TRUE.equals(t.getArchived()))) {
            throw new ConflictException("The " + year.getSessionName() + " syllabus is archived and read-only", "ARCHIVED");
        }
        if (!existing.isEmpty() && coverageRepository.existsByTopicIdIn(existing.stream().map(SyllabusTopic::getTopicId).toList())) {
            throw new ConflictException("Chapters of this plan are already marked as taught; the plan can no longer be replaced", "PLAN_IN_USE");
        }
        topicRepository.deleteAll(existing);
        topicRepository.flush();
        int sequence = 0;
        List<SyllabusTopic> saved = new ArrayList<>();
        for (SyllabusTopicRequest topic : request.getTopics()) {
            saved.add(topicRepository.save(SyllabusTopic.builder().tenantId(tenant).academicYearId(year.getAcademicYearId())
                    .gradeId(grade.getGradeId()).subjectId(subject.getSubjectId()).sequenceNo(++sequence)
                    .chapterTitle(topic.getChapterTitle().trim()).plannedCompletionDate(topic.getPlannedCompletionDate())
                    .plannedPeriods(topic.getPlannedPeriods()).build()));
        }
        audit.logSuccess("SYLLABUS_PLAN", "SYLLABUS_PLAN_SAVED", grade.getGradeName() + " " + subject.getSubjectCode() + " "
                + year.getSessionName() + ": " + saved.size() + " chapters");
        return toPlan(year.getAcademicYearId(), grade, subject, saved, Map.of(), null);
    }

    @Transactional(readOnly = true)
    public List<SyllabusPlanResponse> listPlans(UUID academicYearId, UUID gradeId, UUID subjectId) {
        String tenant = ctx.tenant(null);
        Grade grade = ctx.grade(gradeId);
        AcademicSession year = academicYearId == null ? ctx.academicYear(tenant, LocalDate.now()) : ctx.academicYearById(tenant, academicYearId);
        Map<UUID, Subject> subjects = ctx.subjects(tenant);
        return topicRepository.findByTenantIdAndAcademicYearIdAndGradeIdOrderBySubjectIdAscSequenceNoAsc(tenant, year.getAcademicYearId(), gradeId)
                .stream().filter(t -> subjectId == null || subjectId.equals(t.getSubjectId()))
                .collect(Collectors.groupingBy(SyllabusTopic::getSubjectId, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> toPlan(year.getAcademicYearId(), grade, subjects.get(e.getKey()), e.getValue(), Map.of(), null))
                .sorted(Comparator.comparing(SyllabusPlanResponse::getSubjectCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Marks a chapter as taught in a section (or corrects the date / notes). */
    public SyllabusTopicResponse markCovered(UUID topicId, CoverageRequest request) {
        SyllabusTopic topic = topicRepository.findById(topicId).orElseThrow(() -> new ResourceNotFoundException("Syllabus topic", "topicId", topicId));
        ctx.requireTenant(topic.getTenantId(), "Syllabus topic", "topicId", topicId);
        if (Boolean.TRUE.equals(topic.getArchived())) {
            throw new ConflictException("This chapter belongs to an archived academic year", "ARCHIVED");
        }
        Section section = ctx.section(request.getSectionId());
        if (!section.getGradeId().equals(topic.getGradeId())) {
            throw new BadRequestException(ctx.className(section) + " does not follow this class's syllabus");
        }
        if (request.getCompletedOn().isAfter(LocalDate.now())) {
            throw new BadRequestException("completedOn cannot be in the future");
        }
        SyllabusCoverage coverage = coverageRepository.findByTopicIdAndSectionId(topicId, section.getSectionId())
                .orElseGet(() -> SyllabusCoverage.builder().topicId(topicId).sectionId(section.getSectionId()).build());
        coverage.setCompletedOn(request.getCompletedOn());
        coverage.setCompletedBy(ctx.actor());
        coverage.setNotes(request.getNotes());
        coverageRepository.save(coverage);
        audit.logSuccess("SYLLABUS_COVERAGE", "SYLLABUS_TOPIC_COVERED", ctx.className(section) + ": " + topic.getChapterTitle());
        return toTopic(topic, coverage.getCompletedOn(), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public SyllabusCoverageResponse coverage(UUID sectionId, UUID subjectId, LocalDate asOf) {
        Section section = ctx.section(sectionId);
        String tenant = ctx.tenantOf(section);
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        AcademicSession year = ctx.academicYear(tenant, date);
        Map<UUID, Subject> subjects = ctx.subjects(tenant);
        Map<UUID, LocalDate> covered = coverageRepository.findBySectionId(sectionId).stream()
                .collect(Collectors.toMap(SyllabusCoverage::getTopicId, SyllabusCoverage::getCompletedOn, (a, b) -> a));
        List<SubjectCoverage> rows = topicRepository
                .findByTenantIdAndAcademicYearIdAndGradeIdOrderBySubjectIdAscSequenceNoAsc(tenant, year.getAcademicYearId(), section.getGradeId())
                .stream().filter(t -> subjectId == null || subjectId.equals(t.getSubjectId()))
                .collect(Collectors.groupingBy(SyllabusTopic::getSubjectId, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> subjectCoverage(subjects.get(e.getKey()), e.getValue(), covered, date))
                .sorted(Comparator.comparing(SubjectCoverage::getSubjectCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return SyllabusCoverageResponse.builder().sectionId(sectionId).className(ctx.className(section)).asOf(date).subjects(rows).build();
    }

    @Transactional(readOnly = true)
    public SyllabusCoverageResponse studentCoverage(UUID studentId, UUID subjectId) {
        StudentMaster student = ctx.student(studentId);
        if (student.getSectionId() == null) {
            throw new ConflictException(AcademicContext.studentName(student) + " is not placed in a section yet", "NO_SECTION");
        }
        return coverage(student.getSectionId(), subjectId, null);
    }

    private SubjectCoverage subjectCoverage(Subject subject, List<SyllabusTopic> topics, Map<UUID, LocalDate> covered, LocalDate asOf) {
        topics.sort(Comparator.comparing(SyllabusTopic::getSequenceNo));
        int total = topics.size();
        int done = (int) topics.stream().filter(t -> covered.containsKey(t.getTopicId())).count();
        int due = (int) topics.stream().filter(t -> !t.getPlannedCompletionDate().isAfter(asOf)).count();
        BigDecimal completion = SchedulingService.percent(done, total);
        BigDecimal expected = SchedulingService.percent(due, total);
        BigDecimal variance = completion.subtract(expected);
        String status = variance.compareTo(TOLERANCE.negate()) < 0 ? "BEHIND" : variance.compareTo(TOLERANCE) > 0 ? "AHEAD" : "ON_TRACK";
        String next = topics.stream().filter(t -> !covered.containsKey(t.getTopicId())).map(SyllabusTopic::getChapterTitle).findFirst().orElse(null);
        return SubjectCoverage.builder().subjectId(subject == null ? null : subject.getSubjectId())
                .subjectCode(subject == null ? null : subject.getSubjectCode()).subjectName(subject == null ? null : subject.getSubjectName())
                .totalTopics(total).completedTopics(done).completionPercent(completion).expectedPercent(expected).variancePercent(variance)
                .status(status).nextTopic(next).topics(topics.stream().map(t -> toTopic(t, covered.get(t.getTopicId()), asOf)).toList()).build();
    }

    private SyllabusPlanResponse toPlan(UUID yearId, Grade grade, Subject subject, List<SyllabusTopic> topics, Map<UUID, LocalDate> covered,
                                        LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        return SyllabusPlanResponse.builder().academicYearId(yearId).gradeId(grade.getGradeId()).gradeName(grade.getGradeName())
                .subjectId(subject == null ? null : subject.getSubjectId()).subjectCode(subject == null ? null : subject.getSubjectCode())
                .subjectName(subject == null ? null : subject.getSubjectName())
                .topics(topics.stream().map(t -> toTopic(t, covered.get(t.getTopicId()), date)).toList()).build();
    }

    static SyllabusTopicResponse toTopic(SyllabusTopic t, LocalDate completedOn, LocalDate asOf) {
        return SyllabusTopicResponse.builder().topicId(t.getTopicId()).sequenceNo(t.getSequenceNo()).chapterTitle(t.getChapterTitle())
                .plannedCompletionDate(t.getPlannedCompletionDate()).plannedPeriods(t.getPlannedPeriods()).completedOn(completedOn)
                .overdue(completedOn == null && t.getPlannedCompletionDate().isBefore(asOf)).archived(t.getArchived()).build();
    }
}
