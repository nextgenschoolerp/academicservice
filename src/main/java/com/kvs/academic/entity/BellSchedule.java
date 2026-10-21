package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A master bell pattern for a branch (US-06000). Only one schedule per branch is active. */
@Entity
@Table(name = "bell_schedules", indexes = {
        @Index(name = "idx_bell_schedule_tenant", columnList = "tenant_id, branch_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BellSchedule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bell_schedule_id")
    private UUID bellScheduleId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 64)
    private String branchId;

    @Column(name = "schedule_name", nullable = false, length = 100)
    private String scheduleName;

    @Column(name = "working_days", nullable = false, length = 20)
    private String workingDays;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = false;
}
