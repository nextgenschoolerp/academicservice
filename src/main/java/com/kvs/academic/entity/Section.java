package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "sections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Section extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "section_name", nullable = false, length = 10)
    private String sectionName;

    @Column(name = "max_capacity")
    @Builder.Default
    private Integer maxCapacity = 40;
}
