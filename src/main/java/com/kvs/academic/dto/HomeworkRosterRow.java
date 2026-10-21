package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A student's state for one homework: MISSING, SUBMITTED, LATE or EVALUATED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkRosterRow {
    private UUID studentId;

    private String studentName;

    private Integer rollNumber;

    private String state;

    private HomeworkSubmissionResponse submission;
}
