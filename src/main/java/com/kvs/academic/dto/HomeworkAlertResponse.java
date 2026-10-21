package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** An overdue alert. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkAlertResponse {
    private UUID alertId;

    private UUID homeworkId;

    private String title;

    private String subjectCode;

    private Instant dueAt;

    private UUID studentId;

    private String studentName;

    private String channel;

    private String message;

    private Instant sentAt;
}
