package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** An overdue-homework alert sent to a student's parent app (US-06016); one per student per assignment. */
@Entity
@Table(name = "homework_overdue_alerts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_alert_homework_student", columnNames = {"homework_id", "student_id"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkAlert extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "homework_id", nullable = false)
    private UUID homeworkId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "message", nullable = false, length = 500)
    private String message;
}
