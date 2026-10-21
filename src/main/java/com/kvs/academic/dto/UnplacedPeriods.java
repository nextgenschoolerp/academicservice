package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Periods the generator could not place without breaking a constraint (US-06002). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnplacedPeriods {
    private String subjectCode;

    private String teacherId;

    private String teacherName;

    private Integer missingPeriods;

    private String reason;
}
