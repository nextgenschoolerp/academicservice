package com.kvs.academic.dto;

import com.kvs.academic.enums.SlotType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

/** One slot of a bell schedule. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BellSlotResponse {
    private Integer sequenceNo;

    private SlotType slotType;

    private Integer periodNumber;

    private String label;

    private LocalTime startTime;

    private LocalTime endTime;

    private Long durationMinutes;
}
