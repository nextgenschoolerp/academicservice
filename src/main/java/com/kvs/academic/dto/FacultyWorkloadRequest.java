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
import java.util.List;

/** US-06001: a teacher's weekly and daily teaching caps (created or updated by teacherId). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyWorkloadRequest {
    @Size(max = 64)
    private String tenantId;

    @Size(max = 64)
    private String branchId;

    @NotBlank
    @Size(max = 64)
    private String teacherId;

    @NotBlank
    @Size(max = 150)
    private String teacherName;

    @NotNull
    @Min(1)
    @Max(60)
    private Integer weeklyMaxPeriods;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer dailyMaxPeriods;

    @Size(max = 20)
    private List<@NotBlank String> qualifiedSubjectCodes;
}
