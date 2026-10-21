package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US-06009: a student's topic mastery radar and subject averages. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAnalyticsResponse {
    private UUID studentId;

    private String studentName;

    private String className;

    private BigDecimal overallMasteryPercent;

    private String level;

    private List<SkillAxis> topics;

    private List<SkillAxis> subjects;

    private List<String> strengths;

    private List<String> focusAreas;
}
