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

/** A chapter of the annual syllabus plan for a class and subject, with its target completion date (US-06004). */
@Entity
@Table(name = "syllabus_topics", indexes = {
        @Index(name = "idx_topic_plan", columnList = "tenant_id, academic_year_id, grade_id, subject_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusTopic extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "chapter_title", nullable = false, length = 200)
    private String chapterTitle;

    @Column(name = "planned_completion_date", nullable = false)
    private LocalDate plannedCompletionDate;

    @Column(name = "planned_periods")
    private Integer plannedPeriods;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean archived = false;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
