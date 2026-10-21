package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.service.SubstitutionService;
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
 * Module 06 substitution. Covers user story:
 * - US-06003: Assign proxy substitute teachers automatically during morning roll call
 */
@RestController
@RequestMapping("/api/v1/lms/substitutions")
public class SubstitutionController {

    @Autowired
    private SubstitutionService service;

    @PostMapping("/absences")
    @PreAuthorize(AcademicRoles.SUBSTITUTION)
    public ResponseEntity<ApiResponse<AbsenceResponse>> reportAbsence(@Valid @RequestBody ReportAbsenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Absence recorded", service.reportAbsence(request)));
    }

    @GetMapping("/absences")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<AbsenceResponse>>> listAbsences(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(service.listAbsences(date)));
    }

    @PostMapping("/auto-assign")
    @PreAuthorize(AcademicRoles.SUBSTITUTION)
    public ResponseEntity<ApiResponse<SubstitutionDayResponse>> autoAssign(@Valid @RequestBody AutoAssignRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Proxies assigned", service.autoAssign(request.getDate())));
    }

    @GetMapping
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<SubstitutionDayResponse>> daySheet(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(service.daySheet(date)));
    }

    @PutMapping("/{proxyId}")
    @PreAuthorize(AcademicRoles.SUBSTITUTION)
    public ResponseEntity<ApiResponse<SubstitutionResponse>> reassign(@PathVariable("proxyId") UUID proxyId,
                                                                      @Valid @RequestBody ReassignProxyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Substitution updated", service.reassign(proxyId, request)));
    }
}
