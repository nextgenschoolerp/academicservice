package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.enums.LessonPlanStatus;
import com.kvs.academic.service.LessonPlanService;
import com.kvs.academic.service.SyllabusService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Module 06 curriculum. Covers user stories:
 * - US-06004: Track syllabus completion percentages against annual academic calendars
 * - US-06013: Track teacher daily lesson plan submissions and principal approval statuses
 * - US-06019: Archive historical lesson plans and syllabus repos for faculty reference
 */
@RestController
@RequestMapping("/api/v1/lms")
public class CurriculumController {

    @Autowired private SyllabusService syllabusService;
    @Autowired private LessonPlanService lessonPlanService;

    // ------------------------------------------------------------------ US-06004

    @PutMapping("/syllabus/plans")
    @PreAuthorize(AcademicRoles.SYLLABUS_PLAN)
    public ResponseEntity<ApiResponse<SyllabusPlanResponse>> savePlan(@Valid @RequestBody SyllabusPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Syllabus plan saved", syllabusService.savePlan(request)));
    }

    @GetMapping("/syllabus/plans")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<SyllabusPlanResponse>>> listPlans(@RequestParam("gradeId") UUID gradeId,
                                                                            @RequestParam(value = "academicYearId", required = false) UUID academicYearId,
                                                                            @RequestParam(value = "subjectId", required = false) UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success(syllabusService.listPlans(academicYearId, gradeId, subjectId)));
    }

    @PostMapping("/syllabus/topics/{topicId}/coverage")
    @PreAuthorize(AcademicRoles.SYLLABUS_COVERAGE)
    public ResponseEntity<ApiResponse<SyllabusTopicResponse>> markCovered(@PathVariable("topicId") UUID topicId,
                                                                          @Valid @RequestBody CoverageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Chapter marked as taught", syllabusService.markCovered(topicId, request)));
    }

    @GetMapping("/syllabus/coverage")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<SyllabusCoverageResponse>> coverage(@RequestParam("sectionId") UUID sectionId,
                                                                          @RequestParam(value = "subjectId", required = false) UUID subjectId,
                                                                          @RequestParam(value = "asOf", required = false)
                                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.success(syllabusService.coverage(sectionId, subjectId, asOf)));
    }

    @GetMapping("/syllabus/students/{studentId}/coverage")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<SyllabusCoverageResponse>> studentCoverage(@PathVariable("studentId") UUID studentId,
                                                                                 @RequestParam(value = "subjectId", required = false) UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success(syllabusService.studentCoverage(studentId, subjectId)));
    }

    // ------------------------------------------------------------------ US-06013

    @PostMapping("/lesson-plans")
    @PreAuthorize(AcademicRoles.LESSON_PLAN_WRITE)
    public ResponseEntity<ApiResponse<LessonPlanResponse>> createPlan(@Valid @RequestBody LessonPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Lesson plan saved", lessonPlanService.create(request)));
    }

    @PutMapping("/lesson-plans/{lessonPlanId}")
    @PreAuthorize(AcademicRoles.LESSON_PLAN_WRITE)
    public ResponseEntity<ApiResponse<LessonPlanResponse>> updatePlan(@PathVariable("lessonPlanId") UUID lessonPlanId,
                                                                      @Valid @RequestBody LessonPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lesson plan updated", lessonPlanService.update(lessonPlanId, request)));
    }

    @PostMapping("/lesson-plans/{lessonPlanId}/submit")
    @PreAuthorize(AcademicRoles.LESSON_PLAN_WRITE)
    public ResponseEntity<ApiResponse<LessonPlanResponse>> submitPlan(@PathVariable("lessonPlanId") UUID lessonPlanId) {
        return ResponseEntity.ok(ApiResponse.success("Lesson plan submitted", lessonPlanService.submit(lessonPlanId)));
    }

    @PostMapping("/lesson-plans/{lessonPlanId}/review")
    @PreAuthorize(AcademicRoles.LESSON_PLAN_REVIEW)
    public ResponseEntity<ApiResponse<LessonPlanResponse>> reviewPlan(@PathVariable("lessonPlanId") UUID lessonPlanId,
                                                                      @Valid @RequestBody ReviewLessonPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lesson plan reviewed", lessonPlanService.review(lessonPlanId, request)));
    }

    @GetMapping("/lesson-plans")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<LessonPlanResponse>>> listPlans(
            @RequestParam(value = "teacherId", required = false) String teacherId,
            @RequestParam(value = "status", required = false) LessonPlanStatus status,
            @RequestParam(value = "sectionId", required = false) UUID sectionId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(lessonPlanService.list(teacherId, status, sectionId, from, to)));
    }

    @GetMapping("/lesson-plans/compliance")
    @PreAuthorize(AcademicRoles.LESSON_PLAN_REVIEW)
    public ResponseEntity<ApiResponse<LessonPlanComplianceResponse>> compliance(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(lessonPlanService.compliance(from, to)));
    }

    // ------------------------------------------------------------------ US-06019

    @PostMapping("/archives")
    @PreAuthorize(AcademicRoles.ARCHIVE)
    public ResponseEntity<ApiResponse<ArchiveResultResponse>> archive(@Valid @RequestBody ArchiveYearRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Academic year archived", lessonPlanService.archiveYear(request)));
    }

    @GetMapping("/archives/lesson-plans")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<LessonPlanResponse>>> archivedPlans(
            @RequestParam(value = "academicYearId", required = false) UUID academicYearId,
            @RequestParam(value = "gradeId", required = false) UUID gradeId,
            @RequestParam(value = "subjectId", required = false) UUID subjectId,
            @RequestParam(value = "q", required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(lessonPlanService.archivedPlans(academicYearId, gradeId, subjectId, q)));
    }

    @GetMapping("/archives/syllabus")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<SyllabusPlanResponse>>> archivedSyllabus(
            @RequestParam(value = "academicYearId", required = false) UUID academicYearId,
            @RequestParam(value = "gradeId", required = false) UUID gradeId,
            @RequestParam(value = "subjectId", required = false) UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success(lessonPlanService.archivedSyllabus(academicYearId, gradeId, subjectId)));
    }
}
