package com.kvs.academic.entity;

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

/** Homework published to a section's parent app dashboards (US-06010). */
@Entity
@Table(name = "homework_assignments", indexes = {
        @Index(name = "idx_homework_section_due", columnList = "section_id, due_at")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkAssignment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "homework_id")
    private UUID homeworkId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", length = 4000)
    private String instructions;

    @Column(name = "assigned_on", nullable = false)
    private LocalDate assignedOn;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "max_marks", nullable = false)
    private Integer maxMarks;

    @Column(name = "asset_id")
    private UUID assetId;
}
