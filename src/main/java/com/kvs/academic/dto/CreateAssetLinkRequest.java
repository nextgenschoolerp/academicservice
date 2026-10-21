package com.kvs.academic.dto;

import com.kvs.academic.enums.AssetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** US-06005: an externally hosted lecture or reference link. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssetLinkRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @NotNull
    private UUID gradeId;

    @NotNull
    private UUID subjectId;

    @Size(max = 200)
    private String chapter;

    private AssetType assetType;

    @NotBlank
    @Size(max = 500)
    @Pattern(regexp = "https://.+", message = "must be an https:// link")
    private String url;

    @Min(1)
    @Max(36000)
    private Integer durationSeconds;
}
