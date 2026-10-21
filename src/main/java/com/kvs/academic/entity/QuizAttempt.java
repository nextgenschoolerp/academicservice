package com.kvs.academic.entity;

import com.kvs.academic.enums.AttemptStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** A student's proctored attempt (US-06007). questionOrderJson lists question ids in display order; optionOrderJson maps a question id to its displayed option permutation; answersJson maps a question id to the chosen original option index. */
@Entity
@Table(name = "lms_quiz_attempts", indexes = {
        @Index(name = "idx_attempt_quiz_student", columnList = "quiz_id, student_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttempt extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "tab_switch_count", nullable = false)
    @Builder.Default
    private Integer tabSwitchCount = 0;

    @Column(name = "is_flagged", nullable = false)
    @Builder.Default
    private Boolean flagged = false;

    @Column(name = "flag_reason", length = 255)
    private String flagReason;

    @Column(name = "question_order_json", nullable = false, length = 8000)
    private String questionOrderJson;

    @Column(name = "option_order_json", nullable = false, length = 8000)
    private String optionOrderJson;

    @Column(name = "answers_json", length = 8000)
    private String answersJson;

    @Column(name = "score")
    private Integer score;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore;

    @Column(name = "started_by", nullable = false, length = 100)
    private String startedBy;
}
