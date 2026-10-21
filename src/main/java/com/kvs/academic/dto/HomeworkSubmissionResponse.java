package com.kvs.academic.dto;

import com.kvs.academic.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A submission and its evaluation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkSubmissionResponse {
    private UUID submissionId;

    private UUID homeworkId;

    private UUID studentId;

    private String studentName;

    private Integer rollNumber;

    private Instant submittedAt;

    private Boolean late;

    private SubmissionStatus status;

    private String submissionText;

    private String attachmentUrl;

    private BigDecimal marksAwarded;

    private Integer maxMarks;

    private String feedback;

    private List<Annotation> annotations;

    private String evaluatedBy;

    private Instant evaluatedAt;
}
