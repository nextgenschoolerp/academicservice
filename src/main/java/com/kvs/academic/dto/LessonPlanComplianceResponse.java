package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

/** US-06013: lesson plan submissions and approvals per teacher. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanComplianceResponse {
    private LocalDate from;

    private LocalDate to;

    private List<TeacherCompliance> teachers;
}
