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

/** A chapter marked as taught in one section (US-06004). */
@Entity
@Table(name = "syllabus_coverage", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coverage_topic_section", columnNames = {"topic_id", "section_id"})
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusCoverage extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "coverage_id")
    private UUID coverageId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "completed_by", nullable = false, length = 100)
    private String completedBy;

    @Column(name = "notes", length = 500)
    private String notes;
}
