package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A teacher's caps, allocated and timetabled load (US-06001). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyWorkloadResponse {
    private UUID workloadId;

    private String teacherId;

    private String teacherName;

    private Integer weeklyMaxPeriods;

    private Integer dailyMaxPeriods;

    private List<String> qualifiedSubjectCodes;

    private Integer allocatedPeriods;

    private Integer timetabledPeriods;

    private Integer remainingPeriods;

    private BigDecimal utilisationPercent;

    private List<AllocationResponse> allocations;
}
