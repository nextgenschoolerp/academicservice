package com.kvs.academic.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** One answer: the displayed option index the student chose. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerItem {
    @NotNull
    private UUID questionId;

    @NotNull
    @Min(0)
    private Integer selectedOption;
}
