package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

/** A planned chapter and, for a section, when it was covered. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusTopicResponse {
    private UUID topicId;

    private Integer sequenceNo;

    private String chapterTitle;

    private LocalDate plannedCompletionDate;

    private Integer plannedPeriods;

    private LocalDate completedOn;

    private Boolean overdue;

    private Boolean archived;
}
