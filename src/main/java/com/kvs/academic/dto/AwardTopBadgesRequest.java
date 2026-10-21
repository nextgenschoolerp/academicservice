package com.kvs.academic.dto;

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

/** US-06017: award a badge to the top N of a section's leaderboard. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardTopBadgesRequest {
    @NotNull
    private UUID sectionId;

    private UUID subjectId;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer topN;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9_]{3,50}", message = "must be 3-50 upper-case letters, digits or '_'")
    private String badgeCode;

    @NotBlank
    @Size(max = 100)
    private String badgeName;

    @Min(0)
    @Max(100)
    private Integer points;
}
