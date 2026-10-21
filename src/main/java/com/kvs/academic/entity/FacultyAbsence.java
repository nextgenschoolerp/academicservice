package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

/** A teacher reported absent for a day at morning roll call (US-06003). */
@Entity
@Table(name = "faculty_absences", indexes = {
        @Index(name = "idx_absence_date", columnList = "tenant_id, absence_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_absence_teacher_date", columnNames = {"tenant_id", "teacher_id", "absence_date"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyAbsence extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "absence_id")
    private UUID absenceId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "absence_date", nullable = false)
    private LocalDate absenceDate;

    @Column(name = "reason", length = 255)
    private String reason;
}
