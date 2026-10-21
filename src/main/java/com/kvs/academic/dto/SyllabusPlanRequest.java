package com.kvs.academic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** US-06004: the annual syllabus plan of a class and subject (replaces the plan while nothing is covered). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusPlanRequest {
    @Size(max = 64)
    private String tenantId;

    private UUID academicYearId;

    @NotNull
    private UUID gradeId;

    @NotNull
    private UUID subjectId;

    @NotEmpty
    @Size(max = 60)
    @Valid
    private List<SyllabusTopicRequest> topics;
}
