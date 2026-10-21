package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** How far a student has watched a lecture video (US-06018). */
@Entity
@Table(name = "video_watch_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_watch_asset_student", columnNames = {"asset_id", "student_id"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoWatchProgress extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "progress_id")
    private UUID progressId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "watched_seconds", nullable = false)
    @Builder.Default
    private Integer watchedSeconds = 0;

    @Column(name = "furthest_position_seconds", nullable = false)
    @Builder.Default
    private Integer furthestPositionSeconds = 0;

    @Column(name = "completion_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal completionPercent = BigDecimal.ZERO;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "last_watched_at", nullable = false)
    private Instant lastWatchedAt;
}
