package com.kvs.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

/** US-06004: a chapter taught in a section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageRequest {
    @NotNull
    private UUID sectionId;

    @NotNull
    private LocalDate completedOn;

    @Size(max = 500)
    private String notes;
}
