package com.kvs.academic.entity;

import com.kvs.academic.enums.LessonPlanStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A teacher's daily lesson plan and its principal review (US-06013); archived with its academic year (US-06019). */
@Entity
@Table(name = "lesson_plans", indexes = {
        @Index(name = "idx_lesson_plan_teacher_date", columnList = "tenant_id, teacher_id, plan_date"),
        @Index(name = "idx_lesson_plan_year", columnList = "tenant_id, academic_year_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlan extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lesson_plan_id")
    private UUID lessonPlanId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "objectives", nullable = false, length = 2000)
    private String objectives;

    @Column(name = "activities", length = 4000)
    private String activities;

    @Column(name = "assessment", length = 2000)
    private String assessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LessonPlanStatus status = LessonPlanStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", length = 1000)
    private String reviewRemarks;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean archived = false;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
