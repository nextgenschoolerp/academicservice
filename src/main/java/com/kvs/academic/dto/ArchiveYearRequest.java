package com.kvs.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** US-06019: archive a closed academic year's lesson plans and syllabus. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveYearRequest {
    @Size(max = 64)
    private String tenantId;

    @NotNull
    private UUID academicYearId;
}
