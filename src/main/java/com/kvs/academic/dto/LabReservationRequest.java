package com.kvs.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** US-06011: reserve a lab slot (optionally for a section). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabReservationRequest {
    @NotNull
    private LocalDate reservationDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    private UUID sectionId;

    @NotBlank
    @Size(max = 255)
    private String purpose;
}
