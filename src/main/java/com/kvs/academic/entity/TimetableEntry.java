package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.util.UUID;

/** One period of a class timetable (module6.sql master_timetables), created by the generator (US-06002). */
@Entity
@Table(name = "master_timetables", indexes = {
        @Index(name = "idx_timetable_lookup", columnList = "grade_id, section_id, day_of_week, period_number"),
        @Index(name = "idx_timetable_teacher", columnList = "tenant_id, teacher_id, day_of_week")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableEntry extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "timetable_id")
    private UUID timetableId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "bell_schedule_id", nullable = false)
    private UUID bellScheduleId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false, length = 64)
    private String teacherId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
