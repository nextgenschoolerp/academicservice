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

/** US-06006: generate multiple-choice questions from chapter text. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQuestionsRequest {
    @NotBlank
    @Size(min = 200, max = 20000)
    private String chapterText;

    @NotNull
    @Min(1)
    @Max(25)
    private Integer count;

    @Size(max = 200)
    private String topic;
}
