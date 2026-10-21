package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.service.HomeworkService;
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
 * Module 06 homework. Covers user stories:
 * - US-06008: Evaluate submitted homework assignments digitally with annotations and marks
 * - US-06010: Publish daily homework assignments directly to parent app dashboards
 * - US-06016: Send automated alerts to students for overdue homework submissions
 */
@RestController
@RequestMapping("/api/v1/lms/homework")
public class HomeworkController {

    @Autowired
    private HomeworkService service;

    // ------------------------------------------------------------------ US-06010

    @PostMapping
    @PreAuthorize(AcademicRoles.HOMEWORK_SET)
    public ResponseEntity<ApiResponse<HomeworkResponse>> create(@Valid @RequestBody CreateHomeworkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Homework published", service.create(request)));
    }

    @GetMapping
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<HomeworkResponse>>> list(@RequestParam("sectionId") UUID sectionId,
                                                                   @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                   @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(service.list(sectionId, from, to)));
    }

    @PostMapping("/{homeworkId}/submissions")
    @PreAuthorize(AcademicRoles.HOMEWORK_SUBMIT)
    public ResponseEntity<ApiResponse<HomeworkSubmissionResponse>> submit(@PathVariable("homeworkId") UUID homeworkId,
                                                                          @Valid @RequestBody SubmitHomeworkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Homework submitted", service.submit(homeworkId, request)));
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<StudentHomeworkResponse>> dashboard(@PathVariable("studentId") UUID studentId,
                                                                          @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return ResponseEntity.ok(ApiResponse.success(service.studentDashboard(studentId, from)));
    }

    // ------------------------------------------------------------------ US-06008

    @GetMapping("/{homeworkId}/submissions")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<HomeworkRosterResponse>> roster(@PathVariable("homeworkId") UUID homeworkId) {
        return ResponseEntity.ok(ApiResponse.success(service.roster(homeworkId)));
    }

    @PutMapping("/submissions/{submissionId}/evaluation")
    @PreAuthorize(AcademicRoles.HOMEWORK_EVALUATE)
    public ResponseEntity<ApiResponse<HomeworkSubmissionResponse>> evaluate(@PathVariable("submissionId") UUID submissionId,
                                                                            @Valid @RequestBody EvaluateHomeworkRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Homework evaluated", service.evaluate(submissionId, request)));
    }

    // ------------------------------------------------------------------ US-06016

    @PostMapping("/overdue-alerts/run")
    @PreAuthorize(AcademicRoles.HOMEWORK_ALERTS)
    public ResponseEntity<ApiResponse<OverdueAlertRunResponse>> runAlerts(@RequestBody(required = false) OverdueAlertRunRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Overdue alerts sent", service.sendOverdueAlerts(request)));
    }

    @GetMapping("/overdue-alerts")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<List<HomeworkAlertResponse>>> alerts(@RequestParam("studentId") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(service.alerts(studentId)));
    }
}
