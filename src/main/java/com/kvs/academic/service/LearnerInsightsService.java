package com.kvs.academic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kvs.academic.dto.*;
import com.kvs.academic.entity.*;
import com.kvs.academic.enums.AttemptStatus;
import com.kvs.academic.enums.RemedialStatus;
import com.kvs.academic.enums.SubmissionStatus;
import com.kvs.academic.repo.*;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Learner insights built from LMS evidence: finished quiz attempts (per question topic), evaluated homework and completed
 * lecture videos. Covers the topic mastery radar (US-06009), the gamified leaderboard and skill badges (US-06017), and
 * remedial class scheduling for students below a threshold (US-06012).
 * Levels: MASTERED from 80%, DEVELOPING from 50%, NEEDS_SUPPORT below. Leaderboard points: a quiz's best attempt counts its
 * score percent (0-100), an evaluated homework half its percent (0-50), a completed video 10, plus badge points.
 */
@Service
@Transactional
public class LearnerInsightsService {

    static final int VIDEO_POINTS = 10;
    private static final TypeReference<Map<UUID, Integer>> ANSWERS = new TypeReference<>() { };
    private static final TypeReference<List<UUID>> IDS = new TypeReference<>() { };

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private JsonText json;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository questionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private HomeworkAssignmentRepository homeworkRepository;
    @Autowired private HomeworkSubmissionRepository submissionRepository;
    @Autowired private VideoWatchProgressRepository watchRepository;
    @Autowired private LearningAssetRepository assetRepository;
    @Autowired private SkillBadgeRepository badgeRepository;
    @Autowired private RemedialSessionRepository remedialRepository;
    @Autowired private TimetableEntryRepository entryRepository;
    @Autowired private StudentMasterRepository studentRepository;

    // ------------------------------------------------------------------ evidence

    /** A scored piece of evidence for one student. */
    private record Mark(UUID studentId, UUID subjectId, String topic, double earned, double possible) {
    }

    /** LMS evidence of a set of students. */
    private final class Evidence {
        final List<Mark> quizMarks = new ArrayList<>();         // per question, topic-level
        final Map<UUID, Map<UUID, Double>> bestQuiz = new HashMap<>();   // student → quiz → best %
        final Map<UUID, UUID> quizSubject = new HashMap<>();
        final List<Mark> homework = new ArrayList<>();          // per evaluated submission
        final List<Mark> videos = new ArrayList<>();            // per completed video (earned 1)
        final Map<UUID, List<SkillBadge>> badges;
        final Map<UUID, Integer> attempts = new HashMap<>();

        Evidence(Collection<UUID> studentIds) {
            List<QuizAttempt> finished = attemptRepository.findByStudentIdIn(studentIds).stream()
                    .filter(a -> a.getStatus() != AttemptStatus.IN_PROGRESS && a.getScore() != null).toList();
            Set<UUID> quizIds = finished.stream().map(QuizAttempt::getQuizId).collect(Collectors.toSet());
            quizRepository.findAllById(quizIds).forEach(q -> quizSubject.put(q.getQuizId(), q.getSubjectId()));
            Map<UUID, QuizQuestion> questions = quizIds.isEmpty() ? Map.of() : questionRepository.findByQuizIdIn(quizIds).stream()
                    .collect(Collectors.toMap(QuizQuestion::getQuestionId, Function.identity()));
            for (QuizAttempt a : finished) {
                UUID subject = quizSubject.get(a.getQuizId());
                attempts.merge(a.getStudentId(), 1, Integer::sum);
                Map<UUID, Integer> answers = json.read(a.getAnswersJson(), ANSWERS, Map.of());
                for (UUID qid : json.read(a.getQuestionOrderJson(), IDS, List.of())) {
                    QuizQuestion q = questions.get(qid);
                    if (q != null) {
                        boolean correct = q.getCorrectOptionIndex().equals(answers.get(qid));
                        quizMarks.add(new Mark(a.getStudentId(), subject, topic(q.getTopic()), correct ? q.getMarks() : 0, q.getMarks()));
                    }
                }
                double pct = a.getMaxScore() == 0 ? 0 : a.getScore() * 100.0 / a.getMaxScore();
                bestQuiz.computeIfAbsent(a.getStudentId(), k -> new HashMap<>()).merge(a.getQuizId(), pct, Math::max);
            }
            List<HomeworkSubmission> evaluated = submissionRepository.findByStudentIdIn(studentIds).stream()
                    .filter(s -> s.getStatus() == SubmissionStatus.EVALUATED && s.getMarksAwarded() != null).toList();
            Map<UUID, HomeworkAssignment> assignments = homeworkRepository.findAllById(evaluated.stream().map(HomeworkSubmission::getHomeworkId)
                    .collect(Collectors.toSet())).stream().collect(Collectors.toMap(HomeworkAssignment::getHomeworkId, Function.identity()));
            for (HomeworkSubmission s : evaluated) {
                HomeworkAssignment h = assignments.get(s.getHomeworkId());
                if (h != null) {
                    homework.add(new Mark(s.getStudentId(), h.getSubjectId(), h.getTitle(), s.getMarksAwarded().doubleValue(), h.getMaxMarks()));
                }
            }
            List<VideoWatchProgress> completed = watchRepository.findByStudentIdIn(studentIds).stream().filter(VideoWatchProgress::getCompleted).toList();
            Map<UUID, LearningAsset> assets = assetRepository.findAllById(completed.stream().map(VideoWatchProgress::getAssetId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(LearningAsset::getAssetId, Function.identity()));
            for (VideoWatchProgress p : completed) {
                LearningAsset asset = assets.get(p.getAssetId());
                videos.add(new Mark(p.getStudentId(), asset == null ? null : asset.getSubjectId(), null, 1, 1));
            }
            badges = badgeRepository.findByStudentIdIn(studentIds).stream().collect(Collectors.groupingBy(SkillBadge::getStudentId));
        }

        /** Average percent of a student's quiz and homework evidence in a subject (null without evidence). */
        BigDecimal subjectAverage(UUID studentId, UUID subjectId) {
            List<Double> percents = new ArrayList<>();
            bestQuiz.getOrDefault(studentId, Map.of()).forEach((quiz, pct) -> {
                if (subjectId.equals(quizSubject.get(quiz))) {
                    percents.add(pct);
                }
            });
            homework.stream().filter(m -> m.studentId().equals(studentId) && subjectId.equals(m.subjectId()))
                    .forEach(m -> percents.add(m.earned() * 100 / m.possible()));
            return percents.isEmpty() ? null : scale(percents.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        }

        int homeworkCount(UUID studentId, UUID subjectId) {
            return (int) homework.stream().filter(m -> m.studentId().equals(studentId) && subjectId.equals(m.subjectId())).count();
        }
    }

    private static String topic(String topic) {
        return topic == null || topic.isBlank() ? "General" : topic;
    }

    // ------------------------------------------------------------------ US-06009 skill analytics

    @Transactional(readOnly = true)
    public SkillAnalyticsResponse studentSkills(UUID studentId) {
        StudentMaster student = ctx.student(studentId);
        Map<UUID, Subject> subjects = ctx.subjects(student.getTenantId());
        Evidence evidence = new Evidence(List.of(studentId));
        List<SkillAxis> topics = topicAxes(evidence.quizMarks, subjects);
        Map<UUID, List<Mark>> bySubject = new LinkedHashMap<>();
        for (Mark m : evidence.quizMarks) {
            bySubject.computeIfAbsent(m.subjectId(), k -> new ArrayList<>()).add(m);
        }
        for (Mark m : evidence.homework) {
            bySubject.computeIfAbsent(m.subjectId(), k -> new ArrayList<>()).add(m);
        }
        List<SkillAxis> subjectAxes = bySubject.entrySet().stream().map(e -> {
            BigDecimal avg = evidence.subjectAverage(studentId, e.getKey());
            Subject s = subjects.get(e.getKey());
            return axis(s == null ? "?" : s.getSubjectName(), s == null ? null : s.getSubjectCode(), avg, e.getValue().size());
        }).sorted(Comparator.comparing(SkillAxis::getAxis)).toList();
        BigDecimal overall = subjectAxes.isEmpty() ? null : scale(subjectAxes.stream().mapToDouble(a -> a.getMasteryPercent().doubleValue()).average().orElse(0));
        List<SkillAxis> ranked = topics.stream().sorted(Comparator.comparing(SkillAxis::getMasteryPercent).reversed()).toList();
        return SkillAnalyticsResponse.builder().studentId(studentId).studentName(AcademicContext.studentName(student))
                .className(ctx.className(student.getSectionId())).overallMasteryPercent(overall).level(overall == null ? "NO_EVIDENCE" : level(overall))
                .topics(topics).subjects(subjectAxes)
                .strengths(ranked.stream().filter(a -> a.getMasteryPercent().doubleValue() >= 80).limit(3).map(SkillAxis::getAxis).toList())
                .focusAreas(ranked.reversed().stream().filter(a -> a.getMasteryPercent().doubleValue() < 50).limit(3).map(SkillAxis::getAxis).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public SectionSkillResponse sectionSkills(UUID sectionId, UUID subjectId) {
        Section section = ctx.section(sectionId);
        List<StudentMaster> roster = ctx.roster(sectionId);
        Map<UUID, Subject> subjects = ctx.subjects(ctx.tenantOf(section));
        Evidence evidence = new Evidence(roster.stream().map(StudentMaster::getStudentId).toList());
        // average of each student's topic mastery, so one student with many attempts does not dominate
        Map<String, List<Mark>> byTopic = evidence.quizMarks.stream().filter(m -> subjectId == null || subjectId.equals(m.subjectId()))
                .collect(Collectors.groupingBy(m -> m.subjectId() + "|" + m.topic(), LinkedHashMap::new, Collectors.toList()));
        List<SkillAxis> axes = byTopic.values().stream().map(marks -> {
            Map<UUID, double[]> perStudent = new HashMap<>();
            for (Mark m : marks) {
                double[] t = perStudent.computeIfAbsent(m.studentId(), k -> new double[2]);
                t[0] += m.earned();
                t[1] += m.possible();
            }
            double avg = perStudent.values().stream().mapToDouble(t -> t[1] == 0 ? 0 : t[0] * 100 / t[1]).average().orElse(0);
            Subject s = subjects.get(marks.get(0).subjectId());
            return axis(marks.get(0).topic(), s == null ? null : s.getSubjectCode(), scale(avg), perStudent.size());
        }).sorted(Comparator.comparing(SkillAxis::getSubjectCode, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(SkillAxis::getAxis)).toList();
        return SectionSkillResponse.builder().sectionId(sectionId).className(ctx.className(section)).students(roster.size()).topics(axes).build();
    }

    private static List<SkillAxis> topicAxes(List<Mark> marks, Map<UUID, Subject> subjects) {
        Map<String, List<Mark>> byTopic = marks.stream().collect(Collectors.groupingBy(m -> m.subjectId() + "|" + m.topic(), LinkedHashMap::new, Collectors.toList()));
        return byTopic.values().stream().map(list -> {
            double earned = list.stream().mapToDouble(Mark::earned).sum();
            double possible = list.stream().mapToDouble(Mark::possible).sum();
            Subject s = subjects.get(list.get(0).subjectId());
            return axis(list.get(0).topic(), s == null ? null : s.getSubjectCode(), scale(possible == 0 ? 0 : earned * 100 / possible), list.size());
        }).sorted(Comparator.comparing(SkillAxis::getSubjectCode, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(SkillAxis::getAxis)).toList();
    }

    private static SkillAxis axis(String name, String subjectCode, BigDecimal mastery, int evidence) {
        BigDecimal value = mastery == null ? BigDecimal.ZERO : mastery;
        return SkillAxis.builder().axis(name).subjectCode(subjectCode).masteryPercent(value).evidenceCount(evidence).level(level(value)).build();
    }

    static String level(BigDecimal percent) {
        double p = percent.doubleValue();
        return p >= 80 ? "MASTERED" : p >= 50 ? "DEVELOPING" : "NEEDS_SUPPORT";
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ US-06017 leaderboard and badges

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(UUID sectionId, UUID subjectId) {
        Section section = ctx.section(sectionId);
        Subject subject = subjectId == null ? null : ctx.subject(subjectId);
        List<StudentMaster> roster = ctx.roster(sectionId);
        Evidence evidence = new Evidence(roster.stream().map(StudentMaster::getStudentId).toList());
        List<LeaderboardRow> rows = new ArrayList<>();
        for (StudentMaster s : roster) {
            UUID id = s.getStudentId();
            double quiz = evidence.bestQuiz.getOrDefault(id, Map.of()).entrySet().stream()
                    .filter(e -> subjectId == null || subjectId.equals(evidence.quizSubject.get(e.getKey()))).mapToDouble(Map.Entry::getValue).sum();
            double homework = evidence.homework.stream().filter(m -> m.studentId().equals(id) && (subjectId == null || subjectId.equals(m.subjectId())))
                    .mapToDouble(m -> m.earned() * 50 / m.possible()).sum();
            double video = evidence.videos.stream().filter(m -> m.studentId().equals(id) && (subjectId == null || subjectId.equals(m.subjectId())))
                    .count() * (double) VIDEO_POINTS;
            List<SkillBadge> badges = evidence.badges.getOrDefault(id, List.of()).stream()
                    .filter(b -> subjectId == null || b.getSubjectId() == null || subjectId.equals(b.getSubjectId())).toList();
            int badgePoints = badges.stream().mapToInt(SkillBadge::getPoints).sum();
            rows.add(LeaderboardRow.builder().studentId(id).studentName(AcademicContext.studentName(s)).quizPoints(scale(quiz))
                    .homeworkPoints(scale(homework)).videoPoints(scale(video)).badgePoints(badgePoints)
                    .points(scale(quiz + homework + video + badgePoints)).badges(badges.stream().map(SkillBadge::getBadgeName).toList()).build());
        }
        rows.sort(Comparator.comparing(LeaderboardRow::getPoints).reversed().thenComparing(LeaderboardRow::getStudentName));
        // competition ranking: equal points share a rank (1, 2, 2, 4)
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i > 0 && rows.get(i).getPoints().compareTo(rows.get(i - 1).getPoints()) == 0 ? rows.get(i - 1).getRank() : i + 1);
        }
        return LeaderboardResponse.builder().sectionId(sectionId).className(ctx.className(section)).subjectId(subjectId)
                .subjectCode(subject == null ? null : subject.getSubjectCode()).rows(rows).build();
    }

    /** Awards the badge to everyone ranked within the top N (ties included) who has earned points and does not hold it yet. */
    public BadgeAwardResponse awardTop(AwardTopBadgesRequest request) {
        LeaderboardResponse board = leaderboard(request.getSectionId(), request.getSubjectId());
        int held = 0;
        List<BadgeResponse> awarded = new ArrayList<>();
        for (LeaderboardRow row : board.getRows()) {
            if (row.getRank() > request.getTopN() || row.getPoints().signum() <= 0) {
                continue;
            }
            if (holds(row.getStudentId(), request.getBadgeCode(), request.getSubjectId())) {
                held++;
                continue;
            }
            StudentMaster student = studentRepository.findById(row.getStudentId()).orElseThrow();
            awarded.add(toResponse(save(student, request.getSectionId(), request.getSubjectId(), request.getBadgeCode(), request.getBadgeName(),
                    request.getPoints(), "Rank " + row.getRank() + " on the " + board.getClassName()
                            + (board.getSubjectCode() == null ? "" : " " + board.getSubjectCode()) + " leaderboard (" + row.getPoints() + " points)"), student));
        }
        audit.logSuccess("SKILL_BADGE", "SKILL_BADGES_AWARDED", request.getBadgeCode() + " to " + awarded.size() + " students of " + board.getClassName());
        return BadgeAwardResponse.builder().awarded(awarded).alreadyHeld(held).build();
    }

    public BadgeResponse award(AwardBadgeRequest request) {
        StudentMaster student = ctx.student(request.getStudentId());
        if (request.getSubjectId() != null) {
            ctx.subject(request.getSubjectId());
        }
        if (holds(student.getStudentId(), request.getBadgeCode(), request.getSubjectId())) {
            throw new ConflictException(AcademicContext.studentName(student) + " already holds " + request.getBadgeCode(), "BADGE_ALREADY_AWARDED");
        }
        SkillBadge badge = save(student, student.getSectionId(), request.getSubjectId(), request.getBadgeCode(), request.getBadgeName(),
                request.getPoints(), request.getReason());
        audit.logSuccess("SKILL_BADGE", "SKILL_BADGE_AWARDED", request.getBadgeCode() + " to " + AcademicContext.studentName(student));
        return toResponse(badge, student);
    }

    @Transactional(readOnly = true)
    public List<BadgeResponse> studentBadges(UUID studentId) {
        StudentMaster student = ctx.student(studentId);
        return badgeRepository.findByStudentIdOrderByAwardedAtDesc(studentId).stream().map(b -> toResponse(b, student)).toList();
    }

    private boolean holds(UUID studentId, String code, UUID subjectId) {
        return badgeRepository.findByStudentIdOrderByAwardedAtDesc(studentId).stream()
                .anyMatch(b -> b.getBadgeCode().equals(code) && Objects.equals(b.getSubjectId(), subjectId));
    }

    private SkillBadge save(StudentMaster student, UUID sectionId, UUID subjectId, String code, String name, Integer points, String reason) {
        return badgeRepository.save(SkillBadge.builder().tenantId(student.getTenantId()).studentId(student.getStudentId()).sectionId(sectionId)
                .subjectId(subjectId).badgeCode(code).badgeName(name.trim()).points(points == null ? 0 : points).reason(reason)
                .awardedBy(ctx.actor()).awardedAt(Instant.now()).build());
    }

    private static BadgeResponse toResponse(SkillBadge b, StudentMaster student) {
        return BadgeResponse.builder().badgeId(b.getBadgeId()).studentId(b.getStudentId()).studentName(AcademicContext.studentName(student))
                .badgeCode(b.getBadgeCode()).badgeName(b.getBadgeName()).subjectId(b.getSubjectId()).points(b.getPoints()).reason(b.getReason())
                .awardedBy(b.getAwardedBy()).awardedAt(b.getAwardedAt()).build();
    }

    // ------------------------------------------------------------------ US-06012 remedial classes

    @Transactional(readOnly = true)
    public RemedialCandidatesResponse remedialCandidates(UUID sectionId, UUID subjectId, BigDecimal threshold) {
        Section section = ctx.section(sectionId);
        Subject subject = ctx.subject(subjectId);
        BigDecimal limit = threshold == null ? BigDecimal.valueOf(50) : threshold;
        List<StudentMaster> roster = ctx.roster(sectionId);
        Evidence evidence = new Evidence(roster.stream().map(StudentMaster::getStudentId).toList());
        List<RemedialCandidate> candidates = new ArrayList<>();
        for (StudentMaster s : roster) {
            BigDecimal avg = evidence.subjectAverage(s.getStudentId(), subjectId);
            if (avg == null || avg.compareTo(limit) >= 0) {
                continue;
            }
            List<Mark> own = evidence.quizMarks.stream().filter(m -> m.studentId().equals(s.getStudentId()) && subjectId.equals(m.subjectId())).toList();
            String weakest = topicAxes(own, Map.of()).stream().min(Comparator.comparing(SkillAxis::getMasteryPercent)).map(SkillAxis::getAxis).orElse(null);
            int quizAttempts = (int) evidence.bestQuiz.getOrDefault(s.getStudentId(), Map.of()).keySet().stream()
                    .filter(q -> subjectId.equals(evidence.quizSubject.get(q))).count();
            candidates.add(RemedialCandidate.builder().studentId(s.getStudentId()).studentName(AcademicContext.studentName(s))
                    .rollNumber(s.getRollNumber()).averagePercent(avg).quizAttempts(quizAttempts)
                    .homeworkEvaluated(evidence.homeworkCount(s.getStudentId(), subjectId)).weakestTopic(weakest).build());
        }
        candidates.sort(Comparator.comparing(RemedialCandidate::getAveragePercent));
        return RemedialCandidatesResponse.builder().sectionId(sectionId).className(ctx.className(section)).subjectId(subjectId)
                .subjectCode(subject.getSubjectCode()).thresholdPercent(limit).candidates(candidates).build();
    }

    /**
     * Schedules a remedial class. It must not clash with the teacher's timetable or other remedial classes, nor with a
     * regular period of any of the students' sections (students would miss class).
     */
    public RemedialSessionResponse scheduleRemedial(RemedialSessionRequest request) {
        Subject subject = ctx.subject(request.getSubjectId());
        Grade grade = ctx.grade(request.getGradeId());
        String tenant = grade.getTenantId();
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("endTime must be after startTime");
        }
        String teacherId = request.getTeacherId() == null || request.getTeacherId().isBlank() ? ctx.userId() : request.getTeacherId();
        if (!teacherId.equals(ctx.userId()) && !ctx.hasRole("ACADEMIC_COORDINATOR", "SUPER_ADMIN")) {
            throw new ForbiddenException("Only an academic coordinator can schedule a remedial class for another teacher");
        }
        List<StudentMaster> students = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(request.getStudentIds())) {
            StudentMaster s = ctx.student(id);
            if (!grade.getGradeId().equals(s.getGradeId())) {
                throw new BadRequestException(AcademicContext.studentName(s) + " is not in " + grade.getGradeName());
            }
            students.add(s);
        }
        int day = request.getSessionDate().getDayOfWeek().getValue();
        String teacherName = ctx.teacherName(tenant, teacherId);
        entryRepository.findByTenantIdAndTeacherIdOrderByDayOfWeekAscPeriodNumberAsc(tenant, teacherId).stream()
                .filter(e -> e.getDayOfWeek() == day && SchedulingService.overlaps(e.getStartTime(), e.getEndTime(), request.getStartTime(), request.getEndTime()))
                .findFirst().ifPresent(e -> {
                    throw new ConflictException(teacherName + " teaches " + ctx.className(e.getSectionId()) + " at " + e.getStartTime(), "TEACHER_BUSY");
                });
        remedialRepository.findByTenantIdAndTeacherIdAndSessionDate(tenant, teacherId, request.getSessionDate()).stream()
                .filter(r -> r.getStatus() == RemedialStatus.SCHEDULED
                        && SchedulingService.overlaps(r.getStartTime(), r.getEndTime(), request.getStartTime(), request.getEndTime()))
                .findFirst().ifPresent(r -> {
                    throw new ConflictException(teacherName + " already has a remedial class at " + r.getStartTime(), "TEACHER_BUSY");
                });
        Set<UUID> sections = students.stream().map(StudentMaster::getSectionId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<String> inClass = new ArrayList<>();
        for (UUID sectionId : sections) {
            boolean clash = entryRepository.findBySectionIdAndDayOfWeek(sectionId, day).stream()
                    .anyMatch(e -> SchedulingService.overlaps(e.getStartTime(), e.getEndTime(), request.getStartTime(), request.getEndTime()));
            if (clash) {
                inClass.add(ctx.className(sectionId));
            }
        }
        if (!inClass.isEmpty()) {
            throw new ConflictException("Students of " + String.join(", ", inClass) + " have regular periods at that time; schedule the remedial class "
                    + "outside timetabled periods", "STUDENTS_IN_CLASS");
        }
        RemedialSession session = remedialRepository.save(RemedialSession.builder().tenantId(tenant).subjectId(subject.getSubjectId())
                .gradeId(grade.getGradeId()).teacherId(teacherId).sessionDate(request.getSessionDate()).startTime(request.getStartTime())
                .endTime(request.getEndTime()).venue(request.getVenue()).focusTopic(request.getFocusTopic().trim())
                .studentIdsJson(json.write(students.stream().map(StudentMaster::getStudentId).toList())).build());
        audit.logSuccess("REMEDIAL_SESSION", "REMEDIAL_SESSION_SCHEDULED", grade.getGradeName() + " " + subject.getSubjectCode() + " on "
                + request.getSessionDate() + " " + request.getStartTime() + " for " + students.size() + " students");
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<RemedialSessionResponse> listRemedial(LocalDate from, LocalDate to, String teacherId) {
        String tenant = ctx.tenant(null);
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(30) : to;
        return remedialRepository.findByTenantIdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(tenant, start, end).stream()
                .filter(r -> teacherId == null || teacherId.equals(r.getTeacherId())).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RemedialSessionResponse> studentRemedial(UUID studentId) {
        StudentMaster student = ctx.student(studentId);
        return remedialRepository.findByTenantIdOrderBySessionDateDesc(student.getTenantId()).stream()
                .filter(r -> json.read(r.getStudentIdsJson(), IDS, List.of()).contains(studentId)).map(this::toResponse).toList();
    }

    private RemedialSessionResponse toResponse(RemedialSession r) {
        Subject subject = ctx.subjects(r.getTenantId()).get(r.getSubjectId());
        List<RemedialStudent> students = studentRepository.findAllById(json.read(r.getStudentIdsJson(), IDS, List.of())).stream()
                .map(s -> RemedialStudent.builder().studentId(s.getStudentId()).studentName(AcademicContext.studentName(s))
                        .className(ctx.className(s.getSectionId())).build())
                .sorted(Comparator.comparing(RemedialStudent::getStudentName)).toList();
        return RemedialSessionResponse.builder().remedialSessionId(r.getRemedialSessionId()).subjectId(r.getSubjectId())
                .subjectCode(subject == null ? null : subject.getSubjectCode()).gradeId(r.getGradeId()).teacherId(r.getTeacherId())
                .teacherName(ctx.teacherName(r.getTenantId(), r.getTeacherId())).sessionDate(r.getSessionDate()).startTime(r.getStartTime())
                .endTime(r.getEndTime()).venue(r.getVenue()).focusTopic(r.getFocusTopic()).status(r.getStatus()).students(students).build();
    }
}
