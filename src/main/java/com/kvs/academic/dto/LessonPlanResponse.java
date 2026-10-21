package com.kvs.academic.dto;

import com.kvs.academic.enums.LessonPlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A lesson plan and its review. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanResponse {
    private UUID lessonPlanId;

    private UUID academicYearId;

    private String teacherId;

    private String teacherName;

    private UUID sectionId;

    private String className;

    private UUID subjectId;

    private String subjectCode;

    private LocalDate planDate;

    private UUID topicId;

    private String chapterTitle;

    private String objectives;

    private String activities;

    private String assessment;

    private LessonPlanStatus status;

    private Instant submittedAt;

    private String reviewedBy;

    private Instant reviewedAt;

    private String reviewRemarks;

    private Boolean archived;
}
