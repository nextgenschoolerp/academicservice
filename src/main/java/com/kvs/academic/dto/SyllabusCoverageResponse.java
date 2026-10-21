package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** US-06004: syllabus completion against the annual calendar for a section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusCoverageResponse {
    private UUID sectionId;

    private String className;

    private LocalDate asOf;

    private List<SubjectCoverage> subjects;
}
