package com.kvs.academic.dto;

import com.kvs.academic.enums.SlotType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

/** One slot of a bell schedule (US-06000). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BellSlotRequest {
    @NotNull
    private SlotType slotType;

    @NotBlank
    @Size(max = 50)
    private String label;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;
}
