package com.kvs.academic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kvs.academic.dto.*;
import com.kvs.academic.entity.Grade;
import com.kvs.academic.entity.ProctorEvent;
import com.kvs.academic.entity.Quiz;
import com.kvs.academic.entity.QuizAttempt;
import com.kvs.academic.entity.QuizQuestion;
import com.kvs.academic.entity.StudentMaster;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.enums.AttemptStatus;
import com.kvs.academic.enums.ProctorEventType;
import com.kvs.academic.enums.QuestionSource;
import com.kvs.academic.enums.QuizStatus;
import com.kvs.academic.repo.ProctorEventRepository;
import com.kvs.academic.repo.QuizAttemptRepository;
import com.kvs.academic.repo.QuizQuestionRepository;
import com.kvs.academic.repo.QuizRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LMS quizzes: question banks with the chapter-text generator (US-06006), timer, shuffle, tab-switch and availability
 * rules (US-06014), and proctored attempts (US-06007). Each attempt fixes its own question and option order; answers are
 * stored against the original option index. Leaving the quiz tab more often than allowed submits the attempt and flags it;
 * an attempt not submitted by its deadline (plus a short grace for network delay) closes as TIMED_OUT with its saved answers.
 */
@Service
@Slf4j
@Transactional
public class QuizService {

    static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final TypeReference<List<UUID>> IDS = new TypeReference<>() { };
    private static final TypeReference<Map<UUID, List<Integer>>> ORDERS = new TypeReference<>() { };
    private static final TypeReference<Map<UUID, Integer>> ANSWERS = new TypeReference<>() { };

    private final SecureRandom random = new SecureRandom();

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private JsonText json;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository questionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ProctorEventRepository eventRepository;

    // ------------------------------------------------------------------ US-06006 authoring

    public QuizResponse create(CreateQuizRequest request) {
        Grade grade = ctx.grade(request.getGradeId());
        Subject subject = ctx.subject(request.getSubjectId());
        if (request.getSectionId() != null && !ctx.section(request.getSectionId()).getGradeId().equals(grade.getGradeId())) {
            throw new BadRequestException("The section is not in " + grade.getGradeName());
        }
        Quiz quiz = quizRepository.save(Quiz.builder().tenantId(grade.getTenantId()).gradeId(grade.getGradeId()).sectionId(request.getSectionId())
                .subjectId(subject.getSubjectId()).teacherId(ctx.userId()).quizTitle(request.getQuizTitle().trim()).build());
        audit.logSuccess("QUIZ", "QUIZ_CREATED", quiz.getQuizTitle() + " (" + grade.getGradeName() + " " + subject.getSubjectCode() + ")");
        return toResponse(quiz, true);
    }

    public QuestionGenerationResponse generateQuestions(UUID quizId, GenerateQuestionsRequest request) {
        Quiz quiz = editableQuiz(quizId);
        McqGenerator.Result result = McqGenerator.generate(request.getChapterText(), request.getCount());
        if (result.questions().isEmpty()) {
            throw new BadRequestException("No questions could be generated: the text needs complete sentences with distinct key terms "
                    + "(names, dates, terms) that recur across the chapter", "NOT_ENOUGH_KEY_TERMS");
        }
        int next = (int) questionRepository.countByQuizId(quizId);
        List<QuestionResponse> created = new ArrayList<>();
        for (McqGenerator.Generated g : result.questions()) {
            QuizQuestion q = questionRepository.save(QuizQuestion.builder().quizId(quizId).sequenceNo(++next).questionText(trim(g.questionText(), 1000))
                    .optionsJson(json.write(g.options())).correctOptionIndex(g.correctIndex()).topic(request.getTopic())
                    .source(QuestionSource.GENERATED).explanation(trim(g.explanation(), 1000)).build());
            created.add(toResponse(q));
        }
        audit.logSuccess("QUIZ", "QUIZ_QUESTIONS_GENERATED", quiz.getQuizTitle() + ": " + created.size() + " of " + request.getCount());
        return QuestionGenerationResponse.builder().requested(request.getCount()).generated(created.size())
                .sentencesScanned(result.sentencesScanned()).questions(created).build();
    }

    public QuestionResponse addQuestion(UUID quizId, QuestionRequest request) {
        editableQuiz(quizId);
        validateOptions(request);
        QuizQuestion q = questionRepository.save(QuizQuestion.builder().quizId(quizId).sequenceNo((int) questionRepository.countByQuizId(quizId) + 1)
                .questionText(request.getQuestionText().trim()).optionsJson(json.write(request.getOptions().stream().map(String::trim).toList()))
                .correctOptionIndex(request.getCorrectOptionIndex()).topic(request.getTopic())
                .marks(request.getMarks() == null ? 1 : request.getMarks()).source(QuestionSource.MANUAL).explanation(request.getExplanation()).build());
        audit.logSuccess("QUIZ", "QUIZ_QUESTION_ADDED", quizId.toString());
        return toResponse(q);
    }

    public QuestionResponse updateQuestion(UUID quizId, UUID questionId, QuestionRequest request) {
        editableQuiz(quizId);
        validateOptions(request);
        QuizQuestion q = question(quizId, questionId);
        q.setQuestionText(request.getQuestionText().trim());
        q.setOptionsJson(json.write(request.getOptions().stream().map(String::trim).toList()));
        q.setCorrectOptionIndex(request.getCorrectOptionIndex());
        q.setTopic(request.getTopic());
        q.setMarks(request.getMarks() == null ? q.getMarks() : request.getMarks());
        q.setExplanation(request.getExplanation());
        return toResponse(questionRepository.save(q));
    }

    public void deleteQuestion(UUID quizId, UUID questionId) {
        editableQuiz(quizId);
        questionRepository.delete(question(quizId, questionId));
        List<QuizQuestion> rest = questionRepository.findByQuizIdOrderBySequenceNoAsc(quizId);
        for (int i = 0; i < rest.size(); i++) {
            rest.get(i).setSequenceNo(i + 1);
        }
        questionRepository.saveAll(rest);
    }

    // ------------------------------------------------------------------ US-06014 rules and publishing

    public QuizResponse updateSettings(UUID quizId, QuizSettingsRequest request) {
        Quiz quiz = quiz(quizId);
        if (quiz.getStatus() == QuizStatus.CLOSED || attemptRepository.existsByQuizId(quizId)) {
            throw new ConflictException("The rules cannot change once students have started the quiz", "QUIZ_IN_USE");
        }
        if (request.getAvailableFrom() != null && request.getAvailableUntil() != null
                && !request.getAvailableUntil().isAfter(request.getAvailableFrom())) {
            throw new BadRequestException("availableUntil must be after availableFrom");
        }
        if (request.getAvailableFrom() != null && request.getAvailableUntil() != null
                && Duration.between(request.getAvailableFrom(), request.getAvailableUntil()).toMinutes() < request.getDurationMinutes()) {
            throw new BadRequestException("The availability window is shorter than the quiz duration");
        }
        quiz.setDurationMinutes(request.getDurationMinutes());
        quiz.setShuffleQuestions(request.getShuffleQuestions());
        quiz.setShuffleOptions(request.getShuffleOptions());
        quiz.setMaxTabSwitches(request.getMaxTabSwitches());
        quiz.setAttemptsAllowed(request.getAttemptsAllowed());
        quiz.setAvailableFrom(request.getAvailableFrom());
        quiz.setAvailableUntil(request.getAvailableUntil());
        audit.logSuccess("QUIZ", "QUIZ_RULES_UPDATED", quiz.getQuizTitle() + ": " + request.getDurationMinutes() + " min, shuffle q="
                + request.getShuffleQuestions() + " o=" + request.getShuffleOptions() + ", tab switches " + request.getMaxTabSwitches());
        return toResponse(quizRepository.save(quiz), true);
    }

    public QuizResponse publish(UUID quizId) {
        Quiz quiz = editableQuiz(quizId);
        if (questionRepository.countByQuizId(quizId) == 0) {
            throw new ConflictException("Add at least one question before publishing", "QUIZ_HAS_NO_QUESTIONS");
        }
        if (quiz.getAvailableUntil() != null && quiz.getAvailableUntil().isBefore(Instant.now())) {
            throw new ConflictException("The availability window has already ended", "QUIZ_WINDOW_ENDED");
        }
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setPublishedAt(Instant.now());
        audit.logSuccess("QUIZ", "QUIZ_PUBLISHED", quiz.getQuizTitle());
        return toResponse(quizRepository.save(quiz), true);
    }

    @Transactional(readOnly = true)
    public QuizResponse get(UUID quizId) {
        Quiz quiz = quiz(quizId);
        return toResponse(quiz, !ctx.isParentOnly());
    }

    /** Quizzes of a class; a parent passes their child's id and sees only published quizzes for the child's class. */
    @Transactional(readOnly = true)
    public List<QuizResponse> list(UUID gradeId, UUID subjectId, QuizStatus status, UUID studentId) {
        UUID grade = gradeId;
        UUID section = null;
        boolean parent = ctx.isParentOnly();
        if (studentId != null) {
            StudentMaster student = ctx.student(studentId);
            grade = student.getGradeId();
            section = student.getSectionId();
        } else if (parent) {
            throw new BadRequestException("studentId is required");
        }
        String tenant = ctx.tenant(null);
        UUID studentSection = section;
        UUID g = grade;
        return (g == null ? quizRepository.findByTenantIdOrderByCreatedAtDesc(tenant) : quizRepository.findByTenantIdAndGradeIdOrderByCreatedAtDesc(tenant, g))
                .stream()
                .filter(q -> subjectId == null || subjectId.equals(q.getSubjectId()))
                .filter(q -> status == null || status == q.getStatus())
                .filter(q -> !parent || q.getStatus() == QuizStatus.PUBLISHED)
                .filter(q -> studentSection == null || q.getSectionId() == null || studentSection.equals(q.getSectionId()))
                .map(q -> toResponse(q, false)).toList();
    }

    // ------------------------------------------------------------------ US-06007 proctored attempts

    /** Starts an attempt, or returns the student's attempt that is still running. */
    public AttemptResponse start(UUID quizId, StartAttemptRequest request) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new ResourceNotFoundException("Quiz", "quizId", quizId));
        ctx.requireTenant(quiz.getTenantId(), "Quiz", "quizId", quizId);
        StudentMaster student = ctx.student(request.getStudentId());
        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new ConflictException("This quiz is not open", "QUIZ_NOT_OPEN");
        }
        Instant now = Instant.now();
        if (quiz.getAvailableFrom() != null && now.isBefore(quiz.getAvailableFrom())) {
            throw new ConflictException("This quiz opens at " + quiz.getAvailableFrom(), "QUIZ_NOT_OPEN");
        }
        if (quiz.getAvailableUntil() != null && !now.isBefore(quiz.getAvailableUntil())) {
            throw new ConflictException("This quiz closed at " + quiz.getAvailableUntil(), "QUIZ_CLOSED");
        }
        if (!quiz.getGradeId().equals(student.getGradeId()) || (quiz.getSectionId() != null && !quiz.getSectionId().equals(student.getSectionId()))) {
            throw new ForbiddenException("This quiz is for another class");
        }
        List<QuizAttempt> previous = attemptRepository.findByQuizIdAndStudentId(quizId, student.getStudentId());
        for (QuizAttempt attempt : previous) {
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && !expire(attempt, quiz)) {
                return toResponse(attempt, quiz);
            }
        }
        if (previous.size() >= quiz.getAttemptsAllowed()) {
            throw new ConflictException(AcademicContext.studentName(student) + " has used all " + quiz.getAttemptsAllowed() + " attempt(s)",
                    "ATTEMPTS_EXHAUSTED");
        }
        List<QuizQuestion> questions = questionRepository.findByQuizIdOrderBySequenceNoAsc(quizId);
        List<UUID> order = new ArrayList<>(questions.stream().map(QuizQuestion::getQuestionId).toList());
        if (quiz.getShuffleQuestions()) {
            Collections.shuffle(order, random);
        }
        Map<UUID, List<Integer>> optionOrder = new LinkedHashMap<>();
        for (QuizQuestion q : questions) {
            List<Integer> permutation = new ArrayList<>();
            for (int i = 0; i < options(q).size(); i++) {
                permutation.add(i);
            }
            if (quiz.getShuffleOptions()) {
                Collections.shuffle(permutation, random);
            }
            optionOrder.put(q.getQuestionId(), permutation);
        }
        Instant deadline = now.plus(Duration.ofMinutes(quiz.getDurationMinutes()));
        if (quiz.getAvailableUntil() != null && quiz.getAvailableUntil().isBefore(deadline)) {
            deadline = quiz.getAvailableUntil();
        }
        QuizAttempt attempt = attemptRepository.save(QuizAttempt.builder().quizId(quizId).studentId(student.getStudentId()).startedAt(now)
                .deadlineAt(deadline).questionOrderJson(json.write(order)).optionOrderJson(json.write(optionOrder)).answersJson("{}")
                .maxScore(questions.stream().mapToInt(QuizQuestion::getMarks).sum()).startedBy(ctx.actor()).build());
        audit.logSuccess("QUIZ_ATTEMPT", "QUIZ_ATTEMPT_STARTED", quiz.getQuizTitle() + " by " + AcademicContext.studentName(student));
        return toResponse(attempt, quiz);
    }

    public AttemptResponse getAttempt(UUID attemptId) {
        QuizAttempt attempt = attempt(attemptId);
        Quiz quiz = quizRepository.findById(attempt.getQuizId()).orElseThrow();
        expire(attempt, quiz);
        return toResponse(attempt, quiz);
    }

    /** Autosaves answers while the attempt runs. */
    public AttemptResponse saveAnswers(UUID attemptId, AnswersRequest request) {
        QuizAttempt attempt = attempt(attemptId);
        Quiz quiz = quizRepository.findById(attempt.getQuizId()).orElseThrow();
        requireRunning(attempt, quiz);
        merge(attempt, request.getAnswers());
        return toResponse(attemptRepository.save(attempt), quiz);
    }

    /** Records a browser event; too many tab switches submit the attempt and flag it. */
    public AttemptResponse recordEvent(UUID attemptId, ProctorEventRequest request) {
        QuizAttempt attempt = attempt(attemptId);
        Quiz quiz = quizRepository.findById(attempt.getQuizId()).orElseThrow();
        eventRepository.save(ProctorEvent.builder().attemptId(attemptId).eventType(request.getEventType()).occurredAt(Instant.now())
                .detail(request.getDetail()).build());
        if (expire(attempt, quiz) || attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return toResponse(attempt, quiz);
        }
        if (request.getEventType() == ProctorEventType.TAB_SWITCH || request.getEventType() == ProctorEventType.WINDOW_BLUR) {
            attempt.setTabSwitchCount(attempt.getTabSwitchCount() + 1);
            if (attempt.getTabSwitchCount() > quiz.getMaxTabSwitches()) {
                finish(attempt, AttemptStatus.AUTO_SUBMITTED, Instant.now(),
                        "Left the quiz " + attempt.getTabSwitchCount() + " times (limit " + quiz.getMaxTabSwitches() + ")");
                audit.logSuccess("QUIZ_ATTEMPT", "QUIZ_ATTEMPT_AUTO_SUBMITTED", attemptId + ": " + attempt.getFlagReason());
            }
        } else if (request.getEventType() == ProctorEventType.COPY_PASTE && !Boolean.TRUE.equals(attempt.getFlagged())) {
            attempt.setFlagged(true);
            attempt.setFlagReason("Copy or paste was used during the quiz");
        }
        return toResponse(attemptRepository.save(attempt), quiz);
    }

    public AttemptResponse submit(UUID attemptId, AnswersRequest request) {
        QuizAttempt attempt = attempt(attemptId);
        Quiz quiz = quizRepository.findById(attempt.getQuizId()).orElseThrow();
        if (expire(attempt, quiz)) {
            return toResponse(attempt, quiz);
        }
        requireRunning(attempt, quiz);
        if (request != null && request.getAnswers() != null) {
            merge(attempt, request.getAnswers());
        }
        finish(attempt, AttemptStatus.SUBMITTED, Instant.now(), null);
        audit.logSuccess("QUIZ_ATTEMPT", "QUIZ_ATTEMPT_SUBMITTED", attemptId + ": " + attempt.getScore() + "/" + attempt.getMaxScore());
        return toResponse(attemptRepository.save(attempt), quiz);
    }

    public ProctoringReportResponse proctoringReport(UUID quizId) {
        Quiz quiz = quiz(quizId);
        List<ProctoringRow> rows = new ArrayList<>();
        for (QuizAttempt attempt : attemptRepository.findByQuizIdOrderByStartedAtAsc(quizId)) {
            expire(attempt, quiz);
            String name = studentName(attempt.getStudentId());
            rows.add(ProctoringRow.builder().attemptId(attempt.getAttemptId()).studentId(attempt.getStudentId()).studentName(name)
                    .status(attempt.getStatus()).score(attempt.getScore()).maxScore(attempt.getMaxScore())
                    .scorePercent(attempt.getScore() == null ? null : SchedulingService.percent(attempt.getScore(), attempt.getMaxScore()))
                    .tabSwitchCount(attempt.getTabSwitchCount()).events((int) eventRepository.countByAttemptId(attempt.getAttemptId()))
                    .flagged(attempt.getFlagged()).flagReason(attempt.getFlagReason()).startedAt(attempt.getStartedAt())
                    .submittedAt(attempt.getSubmittedAt()).build());
        }
        List<BigDecimal> scores = rows.stream().map(ProctoringRow::getScorePercent).filter(Objects::nonNull).toList();
        BigDecimal average = scores.isEmpty() ? null
                : scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP);
        return ProctoringReportResponse.builder().quizId(quizId).quizTitle(quiz.getQuizTitle()).attempts(rows.size())
                .submitted((int) rows.stream().filter(r -> r.getStatus() != AttemptStatus.IN_PROGRESS).count())
                .flagged((int) rows.stream().filter(ProctoringRow::getFlagged).count()).averageScorePercent(average).rows(rows).build();
    }

    // ------------------------------------------------------------------ attempt mechanics

    /** Closes an expired running attempt as TIMED_OUT; returns true when it did. */
    private boolean expire(QuizAttempt attempt, Quiz quiz) {
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && Instant.now().isAfter(attempt.getDeadlineAt().plus(SUBMIT_GRACE))) {
            finish(attempt, AttemptStatus.TIMED_OUT, attempt.getDeadlineAt(), "The timer ran out before the quiz was submitted");
            attemptRepository.save(attempt);
            return true;
        }
        return false;
    }

    private void requireRunning(QuizAttempt attempt, Quiz quiz) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt is already " + attempt.getStatus(), "ATTEMPT_CLOSED");
        }
        if (expire(attempt, quiz)) {
            throw new ConflictException("The timer has run out", "ATTEMPT_CLOSED");
        }
    }

    private void merge(QuizAttempt attempt, List<AnswerItem> answers) {
        Map<UUID, List<Integer>> orders = json.read(attempt.getOptionOrderJson(), ORDERS, Map.of());
        Map<UUID, Integer> saved = new LinkedHashMap<>(json.read(attempt.getAnswersJson(), ANSWERS, Map.of()));
        for (AnswerItem answer : answers) {
            List<Integer> permutation = orders.get(answer.getQuestionId());
            if (permutation == null) {
                throw new BadRequestException("Question " + answer.getQuestionId() + " is not part of this attempt");
            }
            if (answer.getSelectedOption() >= permutation.size()) {
                throw new BadRequestException("selectedOption must be between 0 and " + (permutation.size() - 1));
            }
            saved.put(answer.getQuestionId(), permutation.get(answer.getSelectedOption()));
        }
        attempt.setAnswersJson(json.write(saved));
    }

    private void finish(QuizAttempt attempt, AttemptStatus status, Instant at, String flagReason) {
        Map<UUID, Integer> answers = json.read(attempt.getAnswersJson(), ANSWERS, Map.of());
        int score = 0;
        for (QuizQuestion q : questionRepository.findByQuizIdOrderBySequenceNoAsc(attempt.getQuizId())) {
            Integer chosen = answers.get(q.getQuestionId());
            if (chosen != null && chosen.equals(q.getCorrectOptionIndex())) {
                score += q.getMarks();
            }
        }
        attempt.setScore(score);
        attempt.setStatus(status);
        attempt.setSubmittedAt(at);
        if (flagReason != null) {
            attempt.setFlagged(true);
            attempt.setFlagReason(flagReason);
        }
    }

    private AttemptResponse toResponse(QuizAttempt attempt, Quiz quiz) {
        Map<UUID, QuizQuestion> questions = questionRepository.findByQuizIdOrderBySequenceNoAsc(quiz.getQuizId()).stream()
                .collect(Collectors.toMap(QuizQuestion::getQuestionId, q -> q));
        Map<UUID, List<Integer>> orders = json.read(attempt.getOptionOrderJson(), ORDERS, Map.of());
        Map<UUID, Integer> answers = json.read(attempt.getAnswersJson(), ANSWERS, Map.of());
        List<AttemptQuestion> shown = new ArrayList<>();
        int number = 0;
        for (UUID id : json.read(attempt.getQuestionOrderJson(), IDS, List.of())) {
            QuizQuestion q = questions.get(id);
            if (q == null) {
                continue;
            }
            List<String> original = options(q);
            List<Integer> permutation = orders.getOrDefault(id, List.of());
            Integer chosen = answers.get(id);
            shown.add(AttemptQuestion.builder().questionId(id).number(++number).questionText(q.getQuestionText())
                    .options(permutation.stream().map(original::get).toList()).marks(q.getMarks())
                    .selectedOption(chosen == null ? null : permutation.indexOf(chosen)).build());
        }
        boolean running = attempt.getStatus() == AttemptStatus.IN_PROGRESS;
        return AttemptResponse.builder().attemptId(attempt.getAttemptId()).quizId(quiz.getQuizId()).quizTitle(quiz.getQuizTitle())
                .studentId(attempt.getStudentId()).status(attempt.getStatus()).startedAt(attempt.getStartedAt()).deadlineAt(attempt.getDeadlineAt())
                .remainingSeconds(running ? Math.max(0, Duration.between(Instant.now(), attempt.getDeadlineAt()).getSeconds()) : 0)
                .tabSwitchCount(attempt.getTabSwitchCount()).maxTabSwitches(quiz.getMaxTabSwitches()).flagged(attempt.getFlagged())
                .flagReason(attempt.getFlagReason()).score(running ? null : attempt.getScore()).maxScore(attempt.getMaxScore())
                .questions(shown).build();
    }

    // ------------------------------------------------------------------ helpers

    private Quiz quiz(UUID quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new ResourceNotFoundException("Quiz", "quizId", quizId));
        ctx.requireTenant(quiz.getTenantId(), "Quiz", "quizId", quizId);
        if (ctx.isParentOnly()) {
            boolean ownClass = quiz.getStatus() == QuizStatus.PUBLISHED && ctx.ownChildren().stream()
                    .anyMatch(s -> quiz.getGradeId().equals(s.getGradeId()));
            if (!ownClass) {
                throw new ForbiddenException("This quiz is not available to your children");
            }
        }
        return quiz;
    }

    private Quiz editableQuiz(UUID quizId) {
        Quiz quiz = quiz(quizId);
        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new ConflictException("Questions can only change while the quiz is a draft; it is " + quiz.getStatus(), "QUIZ_NOT_EDITABLE");
        }
        return quiz;
    }

    private QuizQuestion question(UUID quizId, UUID questionId) {
        return questionRepository.findById(questionId).filter(q -> q.getQuizId().equals(quizId))
                .orElseThrow(() -> new ResourceNotFoundException("Question", "questionId", questionId));
    }

    private QuizAttempt attempt(UUID attemptId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId).orElseThrow(() -> new ResourceNotFoundException("Attempt", "attemptId", attemptId));
        ctx.student(attempt.getStudentId());
        return attempt;
    }

    private static void validateOptions(QuestionRequest request) {
        if (request.getCorrectOptionIndex() >= request.getOptions().size()) {
            throw new BadRequestException("correctOptionIndex must point to one of the " + request.getOptions().size() + " options");
        }
        long distinct = request.getOptions().stream().map(o -> o.trim().toLowerCase(Locale.ROOT)).distinct().count();
        if (distinct < request.getOptions().size()) {
            throw new BadRequestException("Options must be different from each other");
        }
    }

    List<String> options(QuizQuestion q) {
        return json.read(q.getOptionsJson(), STRINGS, List.of());
    }

    private String studentName(UUID studentId) {
        try {
            return AcademicContext.studentName(ctx.student(studentId));
        } catch (RuntimeException e) {
            return studentId.toString();
        }
    }

    private QuizResponse toResponse(Quiz quiz, boolean withQuestions) {
        List<QuizQuestion> questions = questionRepository.findByQuizIdOrderBySequenceNoAsc(quiz.getQuizId());
        Subject subject = ctx.subjects(quiz.getTenantId()).get(quiz.getSubjectId());
        return QuizResponse.builder().quizId(quiz.getQuizId()).quizTitle(quiz.getQuizTitle()).gradeId(quiz.getGradeId()).sectionId(quiz.getSectionId())
                .subjectId(quiz.getSubjectId()).subjectCode(subject == null ? null : subject.getSubjectCode()).teacherId(quiz.getTeacherId())
                .status(quiz.getStatus()).durationMinutes(quiz.getDurationMinutes()).shuffleQuestions(quiz.getShuffleQuestions())
                .shuffleOptions(quiz.getShuffleOptions()).maxTabSwitches(quiz.getMaxTabSwitches()).attemptsAllowed(quiz.getAttemptsAllowed())
                .availableFrom(quiz.getAvailableFrom()).availableUntil(quiz.getAvailableUntil()).publishedAt(quiz.getPublishedAt())
                .questionCount(questions.size()).totalMarks(questions.stream().mapToInt(QuizQuestion::getMarks).sum())
                .questions(withQuestions ? questions.stream().map(this::toResponse).toList() : null).build();
    }

    private QuestionResponse toResponse(QuizQuestion q) {
        return QuestionResponse.builder().questionId(q.getQuestionId()).sequenceNo(q.getSequenceNo()).questionText(q.getQuestionText())
                .options(options(q)).correctOptionIndex(q.getCorrectOptionIndex()).topic(q.getTopic()).marks(q.getMarks()).source(q.getSource())
                .explanation(q.getExplanation()).build();
    }

    private static String trim(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
