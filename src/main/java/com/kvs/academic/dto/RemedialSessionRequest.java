package com.kvs.academic.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** US-06012: a remedial class. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemedialSessionRequest {
    @NotNull
    private UUID subjectId;

    @NotNull
    private UUID gradeId;

    @Size(max = 64)
    private String teacherId;

    @NotNull
    @FutureOrPresent
    private LocalDate sessionDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @Size(max = 100)
    private String venue;

    @NotBlank
    @Size(max = 200)
    private String focusTopic;

    @NotEmpty
    @Size(max = 40)
    private List<@NotNull UUID> studentIds;
}
