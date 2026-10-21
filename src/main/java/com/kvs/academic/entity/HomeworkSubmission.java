package com.kvs.academic.entity;

import com.kvs.academic.enums.SubmissionStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A student's homework submission and its digital evaluation (US-06008). annotationsJson is a JSON array of {anchor, comment}. */
@Entity
@Table(name = "homework_submissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_submission_homework_student", columnNames = {"homework_id", "student_id"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkSubmission extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(name = "homework_id", nullable = false)
    private UUID homeworkId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "submission_text", length = 4000)
    private String submissionText;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "is_late", nullable = false)
    @Builder.Default
    private Boolean late = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(name = "marks_awarded", precision = 6, scale = 2)
    private BigDecimal marksAwarded;

    @Column(name = "feedback", length = 2000)
    private String feedback;

    @Column(name = "annotations_json", length = 8000)
    private String annotationsJson;

    @Column(name = "evaluated_by", length = 100)
    private String evaluatedBy;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "submitted_by", nullable = false, length = 100)
    private String submittedBy;
}
