package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** US-06010: a child's homework dashboard. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentHomeworkResponse {
    private UUID studentId;

    private String studentName;

    private String className;

    private Integer pending;

    private Integer overdue;

    private List<StudentHomeworkItem> items;
}
