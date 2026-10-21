package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US-06012: students needing academic support in a subject. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemedialCandidatesResponse {
    private UUID sectionId;

    private String className;

    private UUID subjectId;

    private String subjectCode;

    private BigDecimal thresholdPercent;

    private List<RemedialCandidate> candidates;
}
