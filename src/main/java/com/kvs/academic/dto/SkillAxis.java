package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/** One axis of the topic mastery radar (US-06009). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAxis {
    private String axis;

    private String subjectCode;

    private BigDecimal masteryPercent;

    private Integer evidenceCount;

    private String level;
}
