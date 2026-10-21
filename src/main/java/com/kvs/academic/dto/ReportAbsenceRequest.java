package com.kvs.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/** US-06003: a teacher absent at morning roll call. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportAbsenceRequest {
    @NotBlank
    @Size(max = 64)
    private String teacherId;

    @NotNull
    private LocalDate absenceDate;

    @Size(max = 255)
    private String reason;
}
