package com.kvs.academic.dto;

import com.kvs.academic.enums.AssignmentMode;
import com.kvs.academic.enums.SubstitutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A proxy substitution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstitutionResponse {
    private UUID proxyId;

    private LocalDate substitutionDate;

    private Integer periodNumber;

    private LocalTime startTime;

    private LocalTime endTime;

    private UUID sectionId;

    private String className;

    private String subjectCode;

    private String absentTeacherId;

    private String absentTeacherName;

    private String proxyTeacherId;

    private String proxyTeacherName;

    private SubstitutionStatus status;

    private AssignmentMode assignmentMode;

    private String matchReason;
}
