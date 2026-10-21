package com.kvs.academic.entity;

import com.kvs.academic.enums.RemedialStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A remedial class for students who need academic support (US-06012). studentIdsJson is a JSON array of student ids. */
@Entity
@Table(name = "remedial_sessions", indexes = {
        @Index(name = "idx_remedial_date", columnList = "tenant_id, session_date")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemedialSession extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "remedial_session_id")
    private UUID remedialSessionId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "venue", length = 100)
    private String venue;

    @Column(name = "focus_topic", nullable = false, length = 200)
    private String focusTopic;

    @Column(name = "student_ids_json", nullable = false, length = 8000)
    private String studentIdsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RemedialStatus status = RemedialStatus.SCHEDULED;
}
