package com.kvs.academic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** US-06000: a master bell pattern; slots are sorted by start time. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBellScheduleRequest {
    @Size(max = 64)
    private String tenantId;

    @NotBlank
    @Size(max = 64)
    private String branchId;

    @NotBlank
    @Size(max = 100)
    private String scheduleName;

    @NotEmpty
    private List<@NotNull @Min(1) @Max(7) Integer> workingDays;

    @NotEmpty
    @Size(max = 20)
    @Valid
    private List<BellSlotRequest> slots;

    private Boolean activate;
}
