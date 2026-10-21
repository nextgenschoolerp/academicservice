package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** Who teaches a subject to a section and for how many periods a week (US-06001). */
@Entity
@Table(name = "subject_allocations", indexes = {
        @Index(name = "idx_allocation_teacher", columnList = "tenant_id, teacher_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_allocation_section_subject", columnNames = {"section_id", "subject_id"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectAllocation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "allocation_id")
    private UUID allocationId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "periods_per_week", nullable = false)
    private Integer periodsPerWeek;
}
