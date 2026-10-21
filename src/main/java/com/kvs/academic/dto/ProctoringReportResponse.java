package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US-06007: attempts, scores and integrity flags of a quiz. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProctoringReportResponse {
    private UUID quizId;

    private String quizTitle;

    private Integer attempts;

    private Integer submitted;

    private Integer flagged;

    private BigDecimal averageScorePercent;

    private List<ProctoringRow> rows;
}
