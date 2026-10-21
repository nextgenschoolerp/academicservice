package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.service.SchedulingService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Module 06 scheduling (academicservice). Covers user stories:
 * - US-06000: Configure daily period schedules, recess times, and master bell patterns
 * - US-06001: Assign faculty workload hours based on weekly maximum teaching caps
 * - US-06002: Generate conflict-free class timetables using automated constraint engines
 * - US-06015: Export master faculty timetables to PDF for staff room noticeboards
 */
@RestController
@RequestMapping("/api/v1/lms")
public class TimetableController {

    @Autowired
    private SchedulingService service;

    // ------------------------------------------------------------------ US-06000

    @PostMapping("/bell-schedules")
    @PreAuthorize(AcademicRoles.BELL_SCHEDULE)
    public ResponseEntity<ApiResponse<BellScheduleResponse>> createSchedule(@Valid @RequestBody CreateBellScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Bell schedule created", service.createSchedule(request)));
    }

    @GetMapping("/bell-schedules")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<BellScheduleResponse>>> listSchedules(@RequestParam(value = "tenantId", required = false) String tenantId,
                                                                               @RequestParam(value = "branchId", required = false) String branchId) {
        return ResponseEntity.ok(ApiResponse.success(service.listSchedules(tenantId, branchId)));
    }

    @GetMapping("/bell-schedules/{bellScheduleId}")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<BellScheduleResponse>> getSchedule(@PathVariable("bellScheduleId") UUID bellScheduleId) {
        return ResponseEntity.ok(ApiResponse.success(service.getSchedule(bellScheduleId)));
    }

    @PostMapping("/bell-schedules/{bellScheduleId}/activate")
    @PreAuthorize(AcademicRoles.BELL_SCHEDULE)
    public ResponseEntity<ApiResponse<BellScheduleResponse>> activate(@PathVariable("bellScheduleId") UUID bellScheduleId) {
        return ResponseEntity.ok(ApiResponse.success("Bell schedule activated", service.activate(bellScheduleId)));
    }

    // ------------------------------------------------------------------ US-06001

    @PostMapping("/subjects")
    @PreAuthorize(AcademicRoles.WORKLOAD)
    public ResponseEntity<ApiResponse<SubjectResponse>> createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Subject created", service.createSubject(request)));
    }

    @GetMapping("/subjects")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> listSubjects(@RequestParam(value = "tenantId", required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.listSubjects(tenantId)));
    }

    @PutMapping("/workloads")
    @PreAuthorize(AcademicRoles.WORKLOAD)
    public ResponseEntity<ApiResponse<FacultyWorkloadResponse>> saveWorkload(@Valid @RequestBody FacultyWorkloadRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Faculty workload saved", service.saveWorkload(request)));
    }

    @GetMapping("/workloads")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<FacultyWorkloadResponse>>> listWorkloads(@RequestParam(value = "tenantId", required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.listWorkloads(tenantId)));
    }

    @GetMapping("/classes")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<ClassSectionResponse>>> listClasses(@RequestParam(value = "tenantId", required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.listClasses(tenantId)));
    }

    @PostMapping("/allocations")
    @PreAuthorize(AcademicRoles.WORKLOAD)
    public ResponseEntity<ApiResponse<AllocationResponse>> allocate(@Valid @RequestBody AllocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Subject allocated", service.allocate(request)));
    }

    @GetMapping("/allocations")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<AllocationResponse>>> listAllocations(@RequestParam(value = "sectionId", required = false) UUID sectionId,
                                                                                @RequestParam(value = "teacherId", required = false) String teacherId) {
        return ResponseEntity.ok(ApiResponse.success(service.listAllocations(sectionId, teacherId)));
    }

    @DeleteMapping("/allocations/{allocationId}")
    @PreAuthorize(AcademicRoles.WORKLOAD)
    public ResponseEntity<ApiResponse<Void>> deleteAllocation(@PathVariable("allocationId") UUID allocationId) {
        service.deleteAllocation(allocationId);
        return ResponseEntity.ok(ApiResponse.ok("Allocation removed"));
    }

    // ------------------------------------------------------------------ US-06002

    @PostMapping("/timetables/generate")
    @PreAuthorize(AcademicRoles.TIMETABLE)
    public ResponseEntity<ApiResponse<TimetableGenerationResponse>> generate(@Valid @RequestBody GenerateTimetableRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Timetables generated", service.generate(request)));
    }

    @PutMapping("/timetables/entries/{timetableId}")
    @PreAuthorize(AcademicRoles.TIMETABLE)
    public ResponseEntity<ApiResponse<TimetableEntryResponse>> moveEntry(@PathVariable("timetableId") UUID timetableId,
                                                                         @Valid @RequestBody MoveTimetableEntryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Period moved", service.moveEntry(timetableId, request)));
    }

    @GetMapping("/timetables/sections/{sectionId}")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<ClassTimetableResponse>> sectionTimetable(@PathVariable("sectionId") UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success(service.sectionTimetable(sectionId)));
    }

    @GetMapping("/timetables/students/{studentId}")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<ClassTimetableResponse>> studentTimetable(@PathVariable("studentId") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(service.studentTimetable(studentId)));
    }

    @GetMapping("/timetables/teachers/{teacherId}")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<TeacherTimetableResponse>> teacherTimetable(@PathVariable("teacherId") String teacherId) {
        return ResponseEntity.ok(ApiResponse.success(service.teacherTimetable(teacherId)));
    }

    // ------------------------------------------------------------------ US-06015

    @GetMapping(value = "/timetables/faculty/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(AcademicRoles.TIMETABLE_EXPORT)
    public ResponseEntity<byte[]> facultyPdf(@RequestParam("bellScheduleId") UUID bellScheduleId,
                                             @RequestParam(value = "teacherId", required = false) String teacherId) {
        byte[] pdf = service.facultyTimetablePdf(bellScheduleId, teacherId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("faculty-timetable.pdf").build().toString())
                .body(pdf);
    }
}
