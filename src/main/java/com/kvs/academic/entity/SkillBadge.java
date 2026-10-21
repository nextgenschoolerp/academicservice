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

/** A digital skill badge awarded to a student (US-06017). */
@Entity
@Table(name = "skill_badges", indexes = {
        @Index(name = "idx_badge_student", columnList = "student_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillBadge extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "badge_id")
    private UUID badgeId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "badge_code", nullable = false, length = 50)
    private String badgeCode;

    @Column(name = "badge_name", nullable = false, length = 100)
    private String badgeName;

    @Column(name = "points", nullable = false)
    @Builder.Default
    private Integer points = 0;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "awarded_by", nullable = false, length = 100)
    private String awardedBy;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;
}
