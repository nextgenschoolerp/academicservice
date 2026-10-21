package com.kvs.academic.entity;

import com.kvs.academic.enums.AssignmentMode;
import com.kvs.academic.enums.SubstitutionStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

/** A proxy teacher covering an absent teacher's period (module6.sql faculty_proxy_substitutions, US-06003). */
@Entity
@Table(name = "faculty_proxy_substitutions", indexes = {
        @Index(name = "idx_proxy_date", columnList = "tenant_id, substitution_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_proxy_period_date", columnNames = {"timetable_id", "substitution_date"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxySubstitution extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "proxy_id")
    private UUID proxyId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "absent_teacher_id", nullable = false, length = 64)
    private String absentTeacherId;

    @Column(name = "proxy_teacher_id", length = 64)
    private String proxyTeacherId;

    @Column(name = "timetable_id", nullable = false)
    private UUID timetableId;

    @Column(name = "substitution_date", nullable = false)
    private LocalDate substitutionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubstitutionStatus status = SubstitutionStatus.ASSIGNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_mode", nullable = false, length = 10)
    private AssignmentMode assignmentMode;

    @Column(name = "match_reason", length = 255)
    private String matchReason;
}
