package com.kvs.academic.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** US-06010: homework for a section, published to the parent app at once. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHomeworkRequest {
    @NotNull
    private UUID sectionId;

    @NotNull
    private UUID subjectId;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String instructions;

    @NotNull
    @Future
    private Instant dueAt;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer maxMarks;

    private UUID assetId;
}
