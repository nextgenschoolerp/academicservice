package com.kvs.academic.dto;

import com.kvs.academic.enums.AssetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** An e-learning asset (US-06005). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningAssetResponse {
    private UUID assetId;

    private String title;

    private AssetType assetType;

    private UUID gradeId;

    private UUID subjectId;

    private String subjectCode;

    private String chapter;

    private String fileName;

    private String contentType;

    private Long sizeBytes;

    private String checksumSha256;

    private String externalUrl;

    private Integer durationSeconds;

    private String contentUrl;

    private String uploadedBy;

    private Instant uploadedAt;
}
