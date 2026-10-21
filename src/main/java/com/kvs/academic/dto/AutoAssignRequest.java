package com.kvs.academic.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/** US-06003: assign proxies for every uncovered period of the day's absent teachers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoAssignRequest {
    @NotNull
    private LocalDate date;
}
