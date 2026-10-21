package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US-06018: watch time and completion for a video in a section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchReportResponse {
    private UUID assetId;

    private String title;

    private Integer durationSeconds;

    private UUID sectionId;

    private String className;

    private Integer students;

    private Integer started;

    private Integer completed;

    private BigDecimal completionRatePercent;

    private BigDecimal averageCompletionPercent;

    private List<WatchReportRow> rows;
}
