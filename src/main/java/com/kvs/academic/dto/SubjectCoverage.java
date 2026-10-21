package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Coverage of one subject in a section (US-06004). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectCoverage {
    private UUID subjectId;

    private String subjectCode;

    private String subjectName;

    private Integer totalTopics;

    private Integer completedTopics;

    private BigDecimal completionPercent;

    private BigDecimal expectedPercent;

    private BigDecimal variancePercent;

    private String status;

    private String nextTopic;

    private List<SyllabusTopicResponse> topics;
}
