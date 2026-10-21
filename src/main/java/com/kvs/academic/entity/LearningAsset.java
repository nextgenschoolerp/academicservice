package com.kvs.academic.entity;

import com.kvs.academic.enums.AssetType;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** An uploaded e-learning asset or an external lecture link (US-06005). */
@Entity
@Table(name = "learning_assets", indexes = {
        @Index(name = "idx_asset_grade_subject", columnList = "tenant_id, grade_id, subject_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningAsset extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "chapter", length = 200)
    private String chapter;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;
}
