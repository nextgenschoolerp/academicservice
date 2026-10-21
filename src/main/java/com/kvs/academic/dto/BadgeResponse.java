package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** An awarded badge. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeResponse {
    private UUID badgeId;

    private UUID studentId;

    private String studentName;

    private String badgeCode;

    private String badgeName;

    private UUID subjectId;

    private Integer points;

    private String reason;

    private String awardedBy;

    private Instant awardedAt;
}
