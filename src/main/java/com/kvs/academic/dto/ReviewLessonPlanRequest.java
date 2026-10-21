package com.kvs.academic.dto;

import com.kvs.academic.enums.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-06013: approve or return a submitted plan (remarks required to return). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewLessonPlanRequest {
    @NotNull
    private ReviewDecision decision;

    @Size(max = 1000)
    private String remarks;
}
