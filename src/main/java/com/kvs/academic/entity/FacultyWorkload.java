package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A teacher's weekly and daily teaching caps and the subjects they may teach (US-06001). teacherId is the authservice user id. */
@Entity
@Table(name = "faculty_workloads", uniqueConstraints = {
        @UniqueConstraint(name = "uk_faculty_workload_teacher", columnNames = {"tenant_id", "teacher_id"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyWorkload extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "workload_id")
    private UUID workloadId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "branch_id", length = 64)
    private String branchId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "teacher_name", nullable = false, length = 150)
    private String teacherName;

    @Column(name = "weekly_max_periods", nullable = false)
    private Integer weeklyMaxPeriods;

    @Column(name = "daily_max_periods", nullable = false)
    private Integer dailyMaxPeriods;

    @Column(name = "qualified_subject_codes", length = 500)
    private String qualifiedSubjectCodes;
}
