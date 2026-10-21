package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

/** A student below the remedial threshold (US-06012). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemedialCandidate {
    private UUID studentId;

    private String studentName;

    private Integer rollNumber;

    private BigDecimal averagePercent;

    private Integer quizAttempts;

    private Integer homeworkEvaluated;

    private String weakestTopic;
}
