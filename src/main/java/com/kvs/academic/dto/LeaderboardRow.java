package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A student's leaderboard line (US-06017). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardRow {
    private Integer rank;

    private UUID studentId;

    private String studentName;

    private BigDecimal points;

    private BigDecimal quizPoints;

    private BigDecimal homeworkPoints;

    private BigDecimal videoPoints;

    private Integer badgePoints;

    private List<String> badges;
}
