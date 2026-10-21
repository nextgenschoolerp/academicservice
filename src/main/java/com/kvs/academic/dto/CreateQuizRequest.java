package com.kvs.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** US-06006: a draft quiz for a class (optionally one section). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuizRequest {
    @Size(max = 64)
    private String tenantId;

    @NotNull
    private UUID gradeId;

    private UUID sectionId;

    @NotNull
    private UUID subjectId;

    @NotBlank
    @Size(max = 200)
    private String quizTitle;
}
