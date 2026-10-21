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
import java.time.LocalDate;

/** A chapter of the annual plan (US-06004). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusTopicRequest {
    @NotBlank
    @Size(max = 200)
    private String chapterTitle;

    @NotNull
    private LocalDate plannedCompletionDate;

    @Min(1)
    @Max(200)
    private Integer plannedPeriods;
}
