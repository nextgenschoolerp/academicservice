package com.kvs.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-06011: why a reservation is cancelled. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelReservationRequest {
    @NotBlank
    @Size(max = 255)
    private String reason;
}
