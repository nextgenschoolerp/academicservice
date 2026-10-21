package com.kvs.academic.dto;

import com.kvs.academic.enums.AttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One attempt in the proctoring report. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProctoringRow {
    private UUID attemptId;

    private UUID studentId;

    private String studentName;

    private AttemptStatus status;

    private Integer score;

    private Integer maxScore;

    private BigDecimal scorePercent;

    private Integer tabSwitchCount;

    private Integer events;

    private Boolean flagged;

    private String flagReason;

    private Instant startedAt;

    private Instant submittedAt;
}
