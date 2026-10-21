package com.kvs.academic.entity;

import com.kvs.core.entity.BaseAuditEntity;
import com.kvs.academic.enums.Gender;
import com.kvs.academic.enums.StudentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_master", indexes = {
        @Index(name = "idx_student_tenant_branch_uid", columnList = "tenant_id, branch_id, student_uid"),
        @Index(name = "idx_student_class_section", columnList = "grade_id, section_id"),
        @Index(name = "idx_student_parent_account", columnList = "parent_account_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentMaster extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "branch_id", nullable = false)
    private String branchId;

    @Column(name = "student_uid", unique = true, nullable = false, length = 50)
    private String studentUid;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(name = "medical_alerts", length = 500)
    private String medicalAlerts;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "grade_id")
    private UUID gradeId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "house_id")
    private UUID houseId;

    @Column(name = "roll_number")
    private Integer rollNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "student_status", nullable = false, length = 20)
    @Builder.Default
    private StudentStatus studentStatus = StudentStatus.ACTIVE;

    @Column(name = "parent_account_id", length = 100)
    private String parentAccountId;

    @Column(name = "id_card_barcode", length = 100)
    private String idCardBarcode;

    @Column(name = "id_card_qr_code", length = 500)
    private String idCardQrCode;

    @Column(name = "profile_photo_url", length = 255)
    private String profilePhotoUrl;

    @Column(name = "residential_address", length = 500)
    private String residentialAddress;

    @Column(name = "transport_route_id", length = 100)
    private String transportRouteId;

    @Column(name = "is_on_hold")
    @Builder.Default
    private Boolean isOnHold = false;

    @Column(name = "active_hold_reason", length = 255)
    private String activeHoldReason;
}
