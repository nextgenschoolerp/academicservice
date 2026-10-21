package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** US-06017: a section's LMS leaderboard. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardResponse {
    private UUID sectionId;

    private String className;

    private UUID subjectId;

    private String subjectCode;

    private List<LeaderboardRow> rows;
}
