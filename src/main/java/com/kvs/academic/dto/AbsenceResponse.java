package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

/** A reported absence. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceResponse {
    private UUID absenceId;

    private String teacherId;

    private String teacherName;

    private LocalDate absenceDate;

    private String reason;

    private Integer periodsAffected;
}
