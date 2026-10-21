package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A homework assignment with submission counts. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkResponse {
    private UUID homeworkId;

    private String title;

    private String instructions;

    private UUID sectionId;

    private String className;

    private UUID subjectId;

    private String subjectCode;

    private String subjectName;

    private String teacherId;

    private String teacherName;

    private LocalDate assignedOn;

    private Instant dueAt;

    private Integer maxMarks;

    private UUID assetId;

    private Integer studentsInSection;

    private Integer submitted;

    private Integer evaluated;

    private Integer missing;
}
