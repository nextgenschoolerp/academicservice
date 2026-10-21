package com.kvs.academic.entity;

import com.kvs.academic.enums.SlotType;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.util.UUID;

/** One slot of a bell schedule (US-06000): a numbered teaching period or a break. */
@Entity
@Table(name = "bell_periods", indexes = {
        @Index(name = "idx_bell_period_schedule", columnList = "bell_schedule_id, sequence_no")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BellPeriod extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bell_period_id")
    private UUID bellPeriodId;

    @Column(name = "bell_schedule_id", nullable = false)
    private UUID bellScheduleId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 20)
    private SlotType slotType;

    @Column(name = "period_number")
    private Integer periodNumber;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
