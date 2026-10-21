package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.service.LearnerInsightsService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Module 06 learner insights. Covers user stories:
 * - US-06009: View student learning skill analytics and topic mastery radar charts
 * - US-06012: Schedule remedial class sessions for students needing academic support
 * - US-06017: Award digital skill badges to top-performing students on LMS leaderboards
 */
@RestController
@RequestMapping("/api/v1/lms")
public class LearnerInsightsController {

    @Autowired
    private LearnerInsightsService service;

    // ------------------------------------------------------------------ US-06009

    @GetMapping("/analytics/students/{studentId}/skills")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<SkillAnalyticsResponse>> studentSkills(@PathVariable("studentId") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(service.studentSkills(studentId)));
    }

    @GetMapping("/analytics/sections/{sectionId}/skills")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<SectionSkillResponse>> sectionSkills(@PathVariable("sectionId") UUID sectionId,
                                                                           @RequestParam(value = "subjectId", required = false) UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success(service.sectionSkills(sectionId, subjectId)));
    }

    // ------------------------------------------------------------------ US-06017

    @GetMapping("/leaderboards/sections/{sectionId}")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<LeaderboardResponse>> leaderboard(@PathVariable("sectionId") UUID sectionId,
                                                                        @RequestParam(value = "subjectId", required = false) UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success(service.leaderboard(sectionId, subjectId)));
    }

    @PostMapping("/badges/award-top")
    @PreAuthorize(AcademicRoles.BADGES)
    public ResponseEntity<ApiResponse<BadgeAwardResponse>> awardTop(@Valid @RequestBody AwardTopBadgesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Badges awarded", service.awardTop(request)));
    }

    @PostMapping("/badges")
    @PreAuthorize(AcademicRoles.BADGES)
    public ResponseEntity<ApiResponse<BadgeResponse>> award(@Valid @RequestBody AwardBadgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Badge awarded", service.award(request)));
    }

    @GetMapping("/badges/students/{studentId}")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> studentBadges(@PathVariable("studentId") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(service.studentBadges(studentId)));
    }

    // ------------------------------------------------------------------ US-06012

    @GetMapping("/remedial/candidates")
    @PreAuthorize(AcademicRoles.REMEDIAL)
    public ResponseEntity<ApiResponse<RemedialCandidatesResponse>> candidates(@RequestParam("sectionId") UUID sectionId,
                                                                              @RequestParam("subjectId") UUID subjectId,
                                                                              @RequestParam(value = "thresholdPercent", required = false) BigDecimal threshold) {
        return ResponseEntity.ok(ApiResponse.success(service.remedialCandidates(sectionId, subjectId, threshold)));
    }

    @PostMapping("/remedial-sessions")
    @PreAuthorize(AcademicRoles.REMEDIAL)
    public ResponseEntity<ApiResponse<RemedialSessionResponse>> schedule(@Valid @RequestBody RemedialSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Remedial class scheduled", service.scheduleRemedial(request)));
    }

    @GetMapping("/remedial-sessions")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<RemedialSessionResponse>>> list(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "teacherId", required = false) String teacherId) {
        return ResponseEntity.ok(ApiResponse.success(service.listRemedial(from, to, teacherId)));
    }

    @GetMapping("/remedial-sessions/students/{studentId}")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<List<RemedialSessionResponse>>> studentSessions(@PathVariable("studentId") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(service.studentRemedial(studentId)));
    }
}
