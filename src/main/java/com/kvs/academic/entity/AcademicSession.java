package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicSession extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "session_name", nullable = false, length = 50)
    private String sessionName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;
}
