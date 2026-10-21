package com.kvs.academic.repo;

import com.kvs.academic.enums.ReservationStatus;
import com.kvs.academic.entity.LabReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LabReservationRepository extends JpaRepository<LabReservation, UUID> {
    List<LabReservation> findByRoomIdAndReservationDateAndStatus(UUID roomId, LocalDate date, ReservationStatus status);
    List<LabReservation> findByTenantIdAndTeacherIdAndReservationDateAndStatus(String tenantId, String teacherId, LocalDate date, ReservationStatus status);
    List<LabReservation> findByRoomIdAndReservationDateBetweenOrderByReservationDateAscStartTimeAsc(UUID roomId, LocalDate from, LocalDate to);
}
