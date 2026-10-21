package com.kvs.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** US-06001: assign a teacher to teach a subject to a section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationRequest {
    @NotNull
    private UUID sectionId;

    @NotNull
    private UUID subjectId;

    @NotBlank
    @Size(max = 64)
    private String teacherId;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer periodsPerWeek;
}
