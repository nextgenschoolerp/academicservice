package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/** One homework on the parent dashboard. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentHomeworkItem {
    private HomeworkResponse homework;

    private String state;

    private Boolean overdue;

    private BigDecimal marksAwarded;

    private String feedback;
}
