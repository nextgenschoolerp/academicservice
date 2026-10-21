package com.kvs.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** US-06002: generate class timetables for the given sections from their allocations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTimetableRequest {
    @NotNull
    private UUID bellScheduleId;

    @NotEmpty
    @Size(max = 60)
    private List<@NotNull UUID> sectionIds;

    @Min(1)
    @Max(4)
    private Integer maxPeriodsPerSubjectPerDay;
}
