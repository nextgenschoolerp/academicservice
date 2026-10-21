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

/** US-06017: award a badge to one student. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardBadgeRequest {
    @NotNull
    private UUID studentId;

    private UUID subjectId;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9_]{3,50}", message = "must be 3-50 upper-case letters, digits or '_'")
    private String badgeCode;

    @NotBlank
    @Size(max = 100)
    private String badgeName;

    @Min(0)
    @Max(100)
    private Integer points;

    @NotBlank
    @Size(max = 255)
    private String reason;
}
