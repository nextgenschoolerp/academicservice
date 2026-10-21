package com.kvs.academic.service;

import com.kvs.academic.dto.*;
import com.kvs.academic.entity.LabReservation;
import com.kvs.academic.entity.LabRoom;
import com.kvs.academic.entity.Section;
import com.kvs.academic.enums.ReservationStatus;
import com.kvs.academic.repo.LabReservationRepository;
import com.kvs.academic.repo.LabRoomRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Computer and science laboratory reservations (US-06011). A room holds one booking at a time, a teacher cannot hold two
 * overlapping bookings, and a section larger than the room's capacity cannot book it. Only the booking teacher or a
 * coordinator / timetable in-charge can cancel.
 */
@Service
@Transactional
public class LabService {

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private LabRoomRepository roomRepository;
    @Autowired private LabReservationRepository reservationRepository;

    public LabRoomResponse createRoom(LabRoomRequest request) {
        String tenant = ctx.tenant(request.getTenantId());
        if (roomRepository.existsByTenantIdAndBranchIdAndRoomNameIgnoreCase(tenant, request.getBranchId(), request.getRoomName().trim())) {
            throw new ConflictException("A room named " + request.getRoomName() + " already exists", "DUPLICATE_ROOM");
        }
        LabRoom room = roomRepository.save(LabRoom.builder().tenantId(tenant).branchId(request.getBranchId()).roomName(request.getRoomName().trim())
                .roomType(request.getRoomType()).capacity(request.getCapacity()).build());
        audit.logSuccess("LAB_ROOM", "LAB_ROOM_CREATED", room.getRoomName() + " (" + room.getRoomType() + ", " + room.getCapacity() + " seats)");
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<LabRoomResponse> listRooms() {
        return roomRepository.findByTenantIdOrderByRoomNameAsc(ctx.tenant(null)).stream().map(LabService::toResponse).toList();
    }

    public LabReservationResponse reserve(UUID roomId, LabReservationRequest request) {
        LabRoom room = room(roomId);
        if (!Boolean.TRUE.equals(room.getActive())) {
            throw new ConflictException(room.getRoomName() + " is not in use", "ROOM_INACTIVE");
        }
        if (request.getReservationDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("reservationDate cannot be in the past");
        }
        LocalTime start = request.getStartTime();
        LocalTime end = request.getEndTime();
        if (!end.isAfter(start)) {
            throw new BadRequestException("endTime must be after startTime");
        }
        Section section = request.getSectionId() == null ? null : ctx.section(request.getSectionId());
        if (section != null) {
            int strength = ctx.roster(section.getSectionId()).size();
            if (strength > room.getCapacity()) {
                throw new ConflictException(ctx.className(section) + " has " + strength + " students; " + room.getRoomName() + " seats "
                        + room.getCapacity(), "CAPACITY_EXCEEDED");
            }
        }
        reservationRepository.findByRoomIdAndReservationDateAndStatus(roomId, request.getReservationDate(), ReservationStatus.BOOKED).stream()
                .filter(r -> SchedulingService.overlaps(r.getStartTime(), r.getEndTime(), start, end)).findFirst()
                .ifPresent(r -> {
                    throw new ConflictException(room.getRoomName() + " is already booked " + r.getStartTime() + "-" + r.getEndTime() + " by "
                            + ctx.teacherName(r.getTenantId(), r.getTeacherId()) + " (" + r.getPurpose() + ")", "LAB_ALREADY_BOOKED");
                });
        String teacherId = ctx.userId();
        reservationRepository.findByTenantIdAndTeacherIdAndReservationDateAndStatus(room.getTenantId(), teacherId, request.getReservationDate(),
                        ReservationStatus.BOOKED).stream()
                .filter(r -> SchedulingService.overlaps(r.getStartTime(), r.getEndTime(), start, end)).findFirst()
                .ifPresent(r -> {
                    throw new ConflictException("You already hold an overlapping booking " + r.getStartTime() + "-" + r.getEndTime(), "TEACHER_DOUBLE_BOOKED");
                });
        LabReservation reservation = reservationRepository.save(LabReservation.builder().roomId(roomId).tenantId(room.getTenantId())
                .reservationDate(request.getReservationDate()).startTime(start).endTime(end)
                .sectionId(section == null ? null : section.getSectionId()).teacherId(teacherId).purpose(request.getPurpose().trim()).build());
        audit.logSuccess("LAB_RESERVATION", "LAB_RESERVED", room.getRoomName() + " " + request.getReservationDate() + " " + start + "-" + end);
        return toResponse(reservation, room);
    }

    @Transactional(readOnly = true)
    public List<LabReservationResponse> listReservations(UUID roomId, LocalDate from, LocalDate to) {
        LabRoom room = room(roomId);
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(14) : to;
        return reservationRepository.findByRoomIdAndReservationDateBetweenOrderByReservationDateAscStartTimeAsc(roomId, start, end).stream()
                .map(r -> toResponse(r, room)).toList();
    }

    public LabReservationResponse cancel(UUID reservationId, CancelReservationRequest request) {
        LabReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "reservationId", reservationId));
        ctx.requireTenant(reservation.getTenantId(), "Reservation", "reservationId", reservationId);
        if (!reservation.getTeacherId().equals(ctx.userId()) && !ctx.hasRole("ACADEMIC_COORDINATOR", "TIMETABLE_INCHARGE", "SUPER_ADMIN")) {
            throw new ForbiddenException("Only the teacher who booked the lab can cancel it");
        }
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw new ConflictException("This reservation is already cancelled", "ALREADY_CANCELLED");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelReason(request.getReason().trim());
        LabReservation saved = reservationRepository.save(reservation);
        audit.logSuccess("LAB_RESERVATION", "LAB_RESERVATION_CANCELLED", reservationId + ": " + request.getReason());
        return toResponse(saved, room(saved.getRoomId()));
    }

    private LabRoom room(UUID roomId) {
        LabRoom room = roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Lab room", "roomId", roomId));
        ctx.requireTenant(room.getTenantId(), "Lab room", "roomId", roomId);
        return room;
    }

    private static LabRoomResponse toResponse(LabRoom r) {
        return LabRoomResponse.builder().roomId(r.getRoomId()).branchId(r.getBranchId()).roomName(r.getRoomName()).roomType(r.getRoomType())
                .capacity(r.getCapacity()).active(r.getActive()).build();
    }

    private LabReservationResponse toResponse(LabReservation r, LabRoom room) {
        return LabReservationResponse.builder().reservationId(r.getReservationId()).roomId(r.getRoomId()).roomName(room.getRoomName())
                .reservationDate(r.getReservationDate()).startTime(r.getStartTime()).endTime(r.getEndTime()).sectionId(r.getSectionId())
                .className(ctx.className(r.getSectionId())).teacherId(r.getTeacherId()).teacherName(ctx.teacherName(r.getTenantId(), r.getTeacherId()))
                .purpose(r.getPurpose()).status(r.getStatus()).cancelReason(r.getCancelReason()).build();
    }
}
