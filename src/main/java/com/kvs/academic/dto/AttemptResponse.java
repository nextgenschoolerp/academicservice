package com.kvs.academic.dto;

import com.kvs.academic.enums.AttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A proctored attempt (US-06007). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptResponse {
    private UUID attemptId;

    private UUID quizId;

    private String quizTitle;

    private UUID studentId;

    private AttemptStatus status;

    private Instant startedAt;

    private Instant deadlineAt;

    private Long remainingSeconds;

    private Integer tabSwitchCount;

    private Integer maxTabSwitches;

    private Boolean flagged;

    private String flagReason;

    private Integer score;

    private Integer maxScore;

    private List<AttemptQuestion> questions;
}
