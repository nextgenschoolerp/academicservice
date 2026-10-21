package com.kvs.academic.entity;

import com.kvs.academic.enums.QuizStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** An LMS quiz (module6.sql lms_quizzes) with its timer and shuffle rules (US-06006, US-06014). */
@Entity
@Table(name = "lms_quizzes", indexes = {
        @Index(name = "idx_quiz_grade_subject", columnList = "tenant_id, grade_id, subject_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quiz extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "quiz_id")
    private UUID quizId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "quiz_title", nullable = false, length = 200)
    private String quizTitle;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 20;

    @Column(name = "shuffle_questions", nullable = false)
    @Builder.Default
    private Boolean shuffleQuestions = true;

    @Column(name = "shuffle_options", nullable = false)
    @Builder.Default
    private Boolean shuffleOptions = true;

    @Column(name = "max_tab_switches", nullable = false)
    @Builder.Default
    private Integer maxTabSwitches = 2;

    @Column(name = "attempts_allowed", nullable = false)
    @Builder.Default
    private Integer attemptsAllowed = 1;

    @Column(name = "available_from")
    private Instant availableFrom;

    @Column(name = "available_until")
    private Instant availableUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private QuizStatus status = QuizStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;
}
