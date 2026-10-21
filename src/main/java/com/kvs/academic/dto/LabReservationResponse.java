package com.kvs.academic.dto;

import com.kvs.academic.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A lab reservation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabReservationResponse {
    private UUID reservationId;

    private UUID roomId;

    private String roomName;

    private LocalDate reservationDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private UUID sectionId;

    private String className;

    private String teacherId;

    private String teacherName;

    private String purpose;

    private ReservationStatus status;

    private String cancelReason;
}
