package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** A bell schedule with its slots and totals (US-06000). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BellScheduleResponse {
    private UUID bellScheduleId;

    private String tenantId;

    private String branchId;

    private String scheduleName;

    private List<Integer> workingDays;

    private Boolean active;

    private Integer teachingPeriodsPerDay;

    private Integer teachingPeriodsPerWeek;

    private Long teachingMinutesPerDay;

    private LocalTime schoolStart;

    private LocalTime schoolEnd;

    private List<BellSlotResponse> slots;
}
