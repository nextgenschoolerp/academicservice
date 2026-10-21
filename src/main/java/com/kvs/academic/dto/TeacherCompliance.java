package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/** A teacher's lesson plan submissions over a period (US-06013). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherCompliance {
    private String teacherId;

    private String teacherName;

    private Integer teachingDays;

    private Integer plansSubmitted;

    private Integer approved;

    private Integer pendingReview;

    private Integer returned;

    private Integer missingDays;

    private BigDecimal compliancePercent;
}
