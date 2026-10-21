package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.service.LabService;
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
 * Module 06 labs. Covers user story:
 * - US-06011: Manage computer lab and science laboratory room reservation schedules
 */
@RestController
@RequestMapping("/api/v1/lms/labs")
public class LabController {

    @Autowired
    private LabService service;

    @PostMapping
    @PreAuthorize(AcademicRoles.LAB_ROOMS)
    public ResponseEntity<ApiResponse<LabRoomResponse>> createRoom(@Valid @RequestBody LabRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Lab room added", service.createRoom(request)));
    }

    @GetMapping
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<LabRoomResponse>>> listRooms() {
        return ResponseEntity.ok(ApiResponse.success(service.listRooms()));
    }

    @PostMapping("/{roomId}/reservations")
    @PreAuthorize(AcademicRoles.LAB_BOOK)
    public ResponseEntity<ApiResponse<LabReservationResponse>> reserve(@PathVariable("roomId") UUID roomId,
                                                                       @Valid @RequestBody LabReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Lab reserved", service.reserve(roomId, request)));
    }

    @GetMapping("/{roomId}/reservations")
    @PreAuthorize(AcademicRoles.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<LabReservationResponse>>> reservations(
            @PathVariable("roomId") UUID roomId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(service.listReservations(roomId, from, to)));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    @PreAuthorize(AcademicRoles.LAB_BOOK)
    public ResponseEntity<ApiResponse<LabReservationResponse>> cancel(@PathVariable("reservationId") UUID reservationId,
                                                                      @Valid @RequestBody CancelReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled", service.cancel(reservationId, request)));
    }
}
