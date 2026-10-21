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

/** US-06006: a question written or edited by the teacher. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {
    @NotBlank
    @Size(max = 1000)
    private String questionText;

    @NotNull
    @Size(min = 2, max = 6)
    private List<@NotBlank @Size(max = 300) String> options;

    @NotNull
    @Min(0)
    private Integer correctOptionIndex;

    @Size(max = 200)
    private String topic;

    @Min(1)
    @Max(10)
    private Integer marks;

    @Size(max = 1000)
    private String explanation;
}
