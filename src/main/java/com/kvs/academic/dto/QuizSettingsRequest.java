package com.kvs.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** US-06014: timer, shuffle, tab-switch and availability rules. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSettingsRequest {
    @NotNull
    @Min(1)
    @Max(180)
    private Integer durationMinutes;

    @NotNull
    private Boolean shuffleQuestions;

    @NotNull
    private Boolean shuffleOptions;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer maxTabSwitches;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer attemptsAllowed;

    private Instant availableFrom;

    private Instant availableUntil;
}
