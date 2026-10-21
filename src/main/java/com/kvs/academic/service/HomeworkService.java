package com.kvs.academic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kvs.academic.dto.*;
import com.kvs.academic.entity.HomeworkAlert;
import com.kvs.academic.entity.HomeworkAssignment;
import com.kvs.academic.entity.HomeworkSubmission;
import com.kvs.academic.entity.LearningAsset;
import com.kvs.academic.entity.Section;
import com.kvs.academic.entity.StudentMaster;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.enums.SubmissionStatus;
import com.kvs.academic.repo.HomeworkAlertRepository;
import com.kvs.academic.repo.HomeworkAssignmentRepository;
import com.kvs.academic.repo.HomeworkSubmissionRepository;
import com.kvs.academic.repo.LearningAssetRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Homework published to parent app dashboards (US-06010), digital evaluation with marks, feedback and annotations
 * (US-06008), and overdue alerts (US-06016). A submission after the due time is accepted but marked late; once evaluated it
 * can no longer be replaced. Each student gets at most one overdue alert per homework, however often the run is repeated.
 */
@Service
@Slf4j
@Transactional
public class HomeworkService {

    static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");
    static final String CHANNEL = "PARENT_APP";
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(SCHOOL_ZONE);
    private static final TypeReference<List<Annotation>> ANNOTATIONS = new TypeReference<>() { };

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private JsonText json;
    @Autowired private HomeworkAssignmentRepository homeworkRepository;
    @Autowired private HomeworkSubmissionRepository submissionRepository;
    @Autowired private HomeworkAlertRepository alertRepository;
    @Autowired private LearningAssetRepository assetRepository;

    // ------------------------------------------------------------------ US-06010 publish and submit

    public HomeworkResponse create(CreateHomeworkRequest request) {
        Section section = ctx.section(request.getSectionId());
        Subject subject = ctx.subject(request.getSubjectId());
        if (request.getAssetId() != null) {
            LearningAsset asset = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Learning asset", "assetId", request.getAssetId()));
            if (!asset.getGradeId().equals(section.getGradeId())) {
                throw new BadRequestException("The attached asset is for another class");
            }
        }
        HomeworkAssignment homework = homeworkRepository.save(HomeworkAssignment.builder().tenantId(subject.getTenantId())
                .gradeId(section.getGradeId()).sectionId(section.getSectionId()).subjectId(subject.getSubjectId()).teacherId(ctx.userId())
                .title(request.getTitle().trim()).instructions(request.getInstructions()).assignedOn(LocalDate.now(SCHOOL_ZONE))
                .dueAt(request.getDueAt()).maxMarks(request.getMaxMarks()).assetId(request.getAssetId()).build());
        audit.logSuccess("HOMEWORK", "HOMEWORK_PUBLISHED", ctx.className(section) + " " + subject.getSubjectCode() + ": " + homework.getTitle()
                + " due " + DUE.format(homework.getDueAt()));
        return toResponse(homework);
    }

    @Transactional(readOnly = true)
    public List<HomeworkResponse> list(UUID sectionId, LocalDate from, LocalDate to) {
        ctx.section(sectionId);
        return homeworkRepository.findBySectionIdOrderByDueAtDesc(sectionId).stream()
                .filter(h -> from == null || !h.getAssignedOn().isBefore(from))
                .filter(h -> to == null || !h.getAssignedOn().isAfter(to))
                .map(this::toResponse).toList();
    }

    public HomeworkSubmissionResponse submit(UUID homeworkId, SubmitHomeworkRequest request) {
        HomeworkAssignment homework = homework(homeworkId);
        StudentMaster student = ctx.student(request.getStudentId());
        if (!homework.getSectionId().equals(student.getSectionId())) {
            throw new BadRequestException(AcademicContext.studentName(student) + " is not in the class this homework was set for");
        }
        if (blank(request.getSubmissionText()) && blank(request.getAttachmentUrl())) {
            throw new BadRequestException("A submission needs text or an attachment");
        }
        HomeworkSubmission submission = submissionRepository.findByHomeworkIdAndStudentId(homeworkId, student.getStudentId())
                .orElseGet(() -> HomeworkSubmission.builder().homeworkId(homeworkId).studentId(student.getStudentId()).build());
        if (submission.getStatus() == SubmissionStatus.EVALUATED) {
            throw new ConflictException("This homework has already been evaluated", "ALREADY_EVALUATED");
        }
        Instant now = Instant.now();
        submission.setSubmittedAt(now);
        submission.setSubmissionText(request.getSubmissionText());
        submission.setAttachmentUrl(request.getAttachmentUrl());
        submission.setLate(now.isAfter(homework.getDueAt()));
        submission.setSubmittedBy(ctx.actor());
        HomeworkSubmission saved = submissionRepository.save(submission);
        audit.logSuccess("HOMEWORK", saved.getLate() ? "HOMEWORK_SUBMITTED_LATE" : "HOMEWORK_SUBMITTED",
                homework.getTitle() + " by " + AcademicContext.studentName(student));
        return toResponse(saved, homework, student);
    }

    /** A child's homework dashboard in the parent app, newest first. */
    @Transactional(readOnly = true)
    public StudentHomeworkResponse studentDashboard(UUID studentId, LocalDate from) {
        StudentMaster student = ctx.student(studentId);
        if (student.getSectionId() == null) {
            throw new ConflictException(AcademicContext.studentName(student) + " is not placed in a section yet", "NO_SECTION");
        }
        LocalDate since = from == null ? LocalDate.now(SCHOOL_ZONE).minusDays(30) : from;
        Instant now = Instant.now();
        List<StudentHomeworkItem> items = new ArrayList<>();
        for (HomeworkAssignment h : homeworkRepository.findBySectionIdOrderByDueAtDesc(student.getSectionId())) {
            if (h.getAssignedOn().isBefore(since)) {
                continue;
            }
            HomeworkSubmission s = submissionRepository.findByHomeworkIdAndStudentId(h.getHomeworkId(), studentId).orElse(null);
            items.add(StudentHomeworkItem.builder().homework(toResponse(h)).state(state(s)).overdue(s == null && now.isAfter(h.getDueAt()))
                    .marksAwarded(s == null ? null : s.getMarksAwarded()).feedback(s == null ? null : s.getFeedback()).build());
        }
        return StudentHomeworkResponse.builder().studentId(studentId).studentName(AcademicContext.studentName(student))
                .className(ctx.className(student.getSectionId()))
                .pending((int) items.stream().filter(i -> "MISSING".equals(i.getState()) && !i.getOverdue()).count())
                .overdue((int) items.stream().filter(StudentHomeworkItem::getOverdue).count()).items(items).build();
    }

    // ------------------------------------------------------------------ US-06008 evaluation

    @Transactional(readOnly = true)
    public HomeworkRosterResponse roster(UUID homeworkId) {
        HomeworkAssignment homework = homework(homeworkId);
        Map<UUID, HomeworkSubmission> submissions = submissionRepository.findByHomeworkId(homeworkId).stream()
                .collect(Collectors.toMap(HomeworkSubmission::getStudentId, s -> s));
        List<HomeworkRosterRow> rows = ctx.roster(homework.getSectionId()).stream().map(student -> {
            HomeworkSubmission s = submissions.get(student.getStudentId());
            return HomeworkRosterRow.builder().studentId(student.getStudentId()).studentName(AcademicContext.studentName(student))
                    .rollNumber(student.getRollNumber()).state(state(s)).submission(s == null ? null : toResponse(s, homework, student)).build();
        }).toList();
        return HomeworkRosterResponse.builder().homework(toResponse(homework)).rows(rows).build();
    }

    public HomeworkSubmissionResponse evaluate(UUID submissionId, EvaluateHomeworkRequest request) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", "submissionId", submissionId));
        HomeworkAssignment homework = homework(submission.getHomeworkId());
        if (!homework.getTeacherId().equals(ctx.userId()) && !ctx.hasRole("ACADEMIC_COORDINATOR", "CLASS_TEACHER", "SUPER_ADMIN")) {
            throw new ForbiddenException("Only the teacher who set this homework can evaluate it");
        }
        if (request.getMarksAwarded().doubleValue() > homework.getMaxMarks()) {
            throw new BadRequestException("marksAwarded cannot exceed the maximum of " + homework.getMaxMarks());
        }
        submission.setMarksAwarded(request.getMarksAwarded());
        submission.setFeedback(request.getFeedback());
        submission.setAnnotationsJson(json.write(request.getAnnotations() == null ? List.of() : request.getAnnotations()));
        submission.setStatus(SubmissionStatus.EVALUATED);
        submission.setEvaluatedBy(ctx.actor());
        submission.setEvaluatedAt(Instant.now());
        HomeworkSubmission saved = submissionRepository.save(submission);
        StudentMaster student = ctx.student(saved.getStudentId());
        audit.logSuccess("HOMEWORK", "HOMEWORK_EVALUATED", homework.getTitle() + " / " + AcademicContext.studentName(student) + ": "
                + request.getMarksAwarded().stripTrailingZeros().toPlainString() + "/" + homework.getMaxMarks());
        return toResponse(saved, homework, student);
    }

    // ------------------------------------------------------------------ US-06016 overdue alerts

    /** Alerts every student who has not submitted homework that is past due; students already alerted are skipped. */
    public OverdueAlertRunResponse sendOverdueAlerts(OverdueAlertRunRequest request) {
        Instant now = Instant.now();
        List<HomeworkAssignment> overdue;
        if (request != null && request.getSectionId() != null) {
            ctx.section(request.getSectionId());
            overdue = homeworkRepository.findBySectionIdAndDueAtBefore(request.getSectionId(), now);
        } else {
            overdue = homeworkRepository.findByTenantIdAndDueAtBefore(ctx.tenant(null), now);
        }
        int already = 0;
        List<HomeworkAlertResponse> sent = new ArrayList<>();
        for (HomeworkAssignment h : overdue) {
            Set<UUID> submitted = submissionRepository.findByHomeworkId(h.getHomeworkId()).stream().map(HomeworkSubmission::getStudentId)
                    .collect(Collectors.toSet());
            Subject subject = ctx.subjects(h.getTenantId()).get(h.getSubjectId());
            String code = subject == null ? "" : subject.getSubjectName();
            for (StudentMaster student : ctx.roster(h.getSectionId())) {
                if (submitted.contains(student.getStudentId())) {
                    continue;
                }
                if (alertRepository.existsByHomeworkIdAndStudentId(h.getHomeworkId(), student.getStudentId())) {
                    already++;
                    continue;
                }
                HomeworkAlert alert = alertRepository.save(HomeworkAlert.builder().homeworkId(h.getHomeworkId()).studentId(student.getStudentId())
                        .tenantId(h.getTenantId()).sentAt(now).channel(CHANNEL)
                        .message(trim("Reminder: " + student.getFirstName() + "'s " + code + " homework \"" + h.getTitle() + "\" was due on "
                                + DUE.format(h.getDueAt()) + " and has not been submitted. Please submit it today.", 500)).build());
                sent.add(toResponse(alert, h, subject, student));
            }
        }
        audit.logSuccess("HOMEWORK", "HOMEWORK_OVERDUE_ALERTS_SENT", overdue.size() + " overdue assignments, " + sent.size() + " alerts sent, "
                + already + " already alerted");
        log.info("Overdue homework run: {} assignments, {} alerts, {} already alerted", overdue.size(), sent.size(), already);
        return OverdueAlertRunResponse.builder().assignmentsChecked(overdue.size()).alertsCreated(sent.size()).alreadyAlerted(already)
                .alerts(sent).build();
    }

    @Transactional(readOnly = true)
    public List<HomeworkAlertResponse> alerts(UUID studentId) {
        StudentMaster student = ctx.student(studentId);
        return alertRepository.findByStudentIdOrderBySentAtDesc(studentId).stream().map(a -> {
            HomeworkAssignment h = homeworkRepository.findById(a.getHomeworkId()).orElse(null);
            Subject subject = h == null ? null : ctx.subjects(h.getTenantId()).get(h.getSubjectId());
            return toResponse(a, h, subject, student);
        }).toList();
    }

    // ------------------------------------------------------------------ helpers

    HomeworkAssignment homework(UUID homeworkId) {
        HomeworkAssignment h = homeworkRepository.findById(homeworkId).orElseThrow(() -> new ResourceNotFoundException("Homework", "homeworkId", homeworkId));
        ctx.requireTenant(h.getTenantId(), "Homework", "homeworkId", homeworkId);
        return h;
    }

    private static String state(HomeworkSubmission s) {
        if (s == null) {
            return "MISSING";
        }
        if (s.getStatus() == SubmissionStatus.EVALUATED) {
            return "EVALUATED";
        }
        return s.getLate() ? "LATE" : "SUBMITTED";
    }

    private HomeworkResponse toResponse(HomeworkAssignment h) {
        Subject subject = ctx.subjects(h.getTenantId()).get(h.getSubjectId());
        List<HomeworkSubmission> submissions = submissionRepository.findByHomeworkId(h.getHomeworkId());
        int roster = ctx.roster(h.getSectionId()).size();
        return HomeworkResponse.builder().homeworkId(h.getHomeworkId()).title(h.getTitle()).instructions(h.getInstructions())
                .sectionId(h.getSectionId()).className(ctx.className(h.getSectionId())).subjectId(h.getSubjectId())
                .subjectCode(subject == null ? null : subject.getSubjectCode()).subjectName(subject == null ? null : subject.getSubjectName())
                .teacherId(h.getTeacherId()).teacherName(ctx.teacherName(h.getTenantId(), h.getTeacherId())).assignedOn(h.getAssignedOn())
                .dueAt(h.getDueAt()).maxMarks(h.getMaxMarks()).assetId(h.getAssetId()).studentsInSection(roster).submitted(submissions.size())
                .evaluated((int) submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.EVALUATED).count())
                .missing(Math.max(0, roster - submissions.size())).build();
    }

    private HomeworkSubmissionResponse toResponse(HomeworkSubmission s, HomeworkAssignment h, StudentMaster student) {
        return HomeworkSubmissionResponse.builder().submissionId(s.getSubmissionId()).homeworkId(s.getHomeworkId()).studentId(s.getStudentId())
                .studentName(AcademicContext.studentName(student)).rollNumber(student.getRollNumber()).submittedAt(s.getSubmittedAt())
                .late(s.getLate()).status(s.getStatus()).submissionText(s.getSubmissionText()).attachmentUrl(s.getAttachmentUrl())
                .marksAwarded(s.getMarksAwarded()).maxMarks(h.getMaxMarks()).feedback(s.getFeedback())
                .annotations(json.read(s.getAnnotationsJson(), ANNOTATIONS, List.of())).evaluatedBy(s.getEvaluatedBy())
                .evaluatedAt(s.getEvaluatedAt()).build();
    }

    private static HomeworkAlertResponse toResponse(HomeworkAlert a, HomeworkAssignment h, Subject subject, StudentMaster student) {
        return HomeworkAlertResponse.builder().alertId(a.getAlertId()).homeworkId(a.getHomeworkId()).title(h == null ? null : h.getTitle())
                .subjectCode(subject == null ? null : subject.getSubjectCode()).dueAt(h == null ? null : h.getDueAt()).studentId(a.getStudentId())
                .studentName(AcademicContext.studentName(student)).channel(a.getChannel()).message(a.getMessage()).sentAt(a.getSentAt()).build();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
