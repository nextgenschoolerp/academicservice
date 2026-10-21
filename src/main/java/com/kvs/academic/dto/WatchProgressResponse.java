package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A student's watch progress on one video. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchProgressResponse {
    private UUID assetId;

    private UUID studentId;

    private Integer watchedSeconds;

    private Integer furthestPositionSeconds;

    private Integer durationSeconds;

    private BigDecimal completionPercent;

    private Boolean completed;

    private Instant lastWatchedAt;
}
