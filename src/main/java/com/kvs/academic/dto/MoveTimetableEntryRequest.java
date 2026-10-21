package com.kvs.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-06002: move a period or change its teacher; the move is refused if it creates a clash. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveTimetableEntryRequest {
    @NotNull
    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @NotNull
    @Min(1)
    @Max(20)
    private Integer periodNumber;

    @Size(max = 64)
    private String teacherId;
}
