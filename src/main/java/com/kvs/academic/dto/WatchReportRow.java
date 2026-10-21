package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One student's line of the watch-time report. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchReportRow {
    private UUID studentId;

    private String studentName;

    private Integer rollNumber;

    private Integer watchedSeconds;

    private BigDecimal completionPercent;

    private Boolean completed;

    private Instant lastWatchedAt;
}
