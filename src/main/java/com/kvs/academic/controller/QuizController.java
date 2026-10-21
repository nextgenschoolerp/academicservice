package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.enums.QuizStatus;
import com.kvs.academic.service.QuizService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Module 06 LMS quizzes. Covers user stories:
 * - US-06006: Auto-generate multiple-choice quiz questions from uploaded chapter text
 * - US-06007: Conduct proctored online assessments with anti-cheat tab-switch detection
 * - US-06014: Configure quiz countdown timers and randomized question order rules
 */
@RestController
@RequestMapping("/api/v1/lms")
public class QuizController {

    @Autowired
    private QuizService service;

    // ------------------------------------------------------------------ US-06006

    @PostMapping("/quizzes")
    @PreAuthorize(AcademicRoles.QUIZ_AUTHOR)
    public ResponseEntity<ApiResponse<QuizResponse>> create(@Valid @RequestBody CreateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Quiz created", service.create(request)));
    }

    @GetMapping("/quizzes")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<List<QuizResponse>>> list(@RequestParam(value = "gradeId", required = false) UUID gradeId,
                                                               @RequestParam(value = "subjectId", required = false) UUID subjectId,
                                                               @RequestParam(value = "status", required = false) QuizStatus status,
                                                               @RequestParam(value = "studentId", required = false) UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(service.list(gradeId, subjectId, status, studentId)));
    }

    @GetMapping("/quizzes/{quizId}")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<QuizResponse>> get(@PathVariable("quizId") UUID quizId) {
        return ResponseEntity.ok(ApiResponse.success(service.get(quizId)));
    }

    @PostMapping("/quizzes/{quizId}/questions/generate")
    @PreAuthorize(AcademicRoles.QUIZ_AUTHOR)
    public ResponseEntity<ApiResponse<QuestionGenerationResponse>> generate(@PathVariable("quizId") UUID quizId,
                                                                            @Valid @RequestBody GenerateQuestionsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Questions generated", service.generateQuestions(quizId, request)));
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @PreAuthorize(AcademicRoles.QUIZ_AUTHOR)
    public ResponseEntity<ApiResponse<QuestionResponse>> addQuestion(@PathVariable("quizId") UUID quizId, @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Question added", service.addQuestion(quizId, request)));
    }

    @PutMapping("/quizzes/{quizId}/questions/{questionId}")
    @PreAuthorize(AcademicRoles.QUIZ_AUTHOR)
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(@PathVariable("quizId") UUID quizId,
                                                                        @PathVariable("questionId") UUID questionId,
                                                                        @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question updated", service.updateQuestion(quizId, questionId, request)));
    }

    @DeleteMapping("/quizzes/{quizId}/questions/{questionId}")
    @PreAuthorize(AcademicRoles.QUIZ_AUTHOR)
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable("quizId") UUID quizId, @PathVariable("questionId") UUID questionId) {
        service.deleteQuestion(quizId, questionId);
        return ResponseEntity.ok(ApiResponse.ok("Question removed"));
    }

    // ------------------------------------------------------------------ US-06014

    @PutMapping("/quizzes/{quizId}/settings")
    @PreAuthorize(AcademicRoles.QUIZ_AUTHOR)
    public ResponseEntity<ApiResponse<QuizResponse>> settings(@PathVariable("quizId") UUID quizId, @Valid @RequestBody QuizSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Quiz rules saved", service.updateSettings(quizId, request)));
    }

    @PostMapping("/quizzes/{quizId}/publish")
    @PreAuthorize(AcademicRoles.QUIZ_AUTHOR)
    public ResponseEntity<ApiResponse<QuizResponse>> publish(@PathVariable("quizId") UUID quizId) {
        return ResponseEntity.ok(ApiResponse.success("Quiz published", service.publish(quizId)));
    }

    // ------------------------------------------------------------------ US-06007

    @PostMapping("/quizzes/{quizId}/attempts")
    @PreAuthorize(AcademicRoles.QUIZ_TAKE)
    public ResponseEntity<ApiResponse<AttemptResponse>> start(@PathVariable("quizId") UUID quizId, @Valid @RequestBody StartAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Attempt started", service.start(quizId, request)));
    }

    @GetMapping("/attempts/{attemptId}")
    @PreAuthorize(AcademicRoles.QUIZ_TAKE)
    public ResponseEntity<ApiResponse<AttemptResponse>> getAttempt(@PathVariable("attemptId") UUID attemptId) {
        return ResponseEntity.ok(ApiResponse.success(service.getAttempt(attemptId)));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    @PreAuthorize(AcademicRoles.QUIZ_TAKE)
    public ResponseEntity<ApiResponse<AttemptResponse>> saveAnswers(@PathVariable("attemptId") UUID attemptId, @Valid @RequestBody AnswersRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Answers saved", service.saveAnswers(attemptId, request)));
    }

    @PostMapping("/attempts/{attemptId}/events")
    @PreAuthorize(AcademicRoles.QUIZ_TAKE)
    public ResponseEntity<ApiResponse<AttemptResponse>> event(@PathVariable("attemptId") UUID attemptId, @Valid @RequestBody ProctorEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Event recorded", service.recordEvent(attemptId, request)));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize(AcademicRoles.QUIZ_TAKE)
    public ResponseEntity<ApiResponse<AttemptResponse>> submit(@PathVariable("attemptId") UUID attemptId,
                                                               @Valid @RequestBody(required = false) AnswersRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Attempt submitted", service.submit(attemptId, request)));
    }

    @GetMapping("/quizzes/{quizId}/proctoring-report")
    @PreAuthorize(AcademicRoles.PROCTOR_REPORT)
    public ResponseEntity<ApiResponse<ProctoringReportResponse>> report(@PathVariable("quizId") UUID quizId) {
        return ResponseEntity.ok(ApiResponse.success(service.proctoringReport(quizId)));
    }
}
