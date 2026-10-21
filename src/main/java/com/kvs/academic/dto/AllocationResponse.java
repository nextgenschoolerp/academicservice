package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A subject-teacher allocation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationResponse {
    private UUID allocationId;

    private UUID sectionId;

    private String className;

    private UUID subjectId;

    private String subjectCode;

    private String subjectName;

    private String teacherId;

    private String teacherName;

    private Integer periodsPerWeek;
}
