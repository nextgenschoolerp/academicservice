package com.kvs.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

/** US-06013: a daily lesson plan (submit = true sends it for approval). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanRequest {
    @NotNull
    private UUID sectionId;

    @NotNull
    private UUID subjectId;

    @NotNull
    private LocalDate planDate;

    private UUID topicId;

    @NotBlank
    @Size(max = 2000)
    private String objectives;

    @Size(max = 4000)
    private String activities;

    @Size(max = 2000)
    private String assessment;

    private Boolean submit;
}
