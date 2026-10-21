package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

/** The day's substitution sheet (US-06003). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstitutionDayResponse {
    private LocalDate date;

    private String dayName;

    private Integer absentTeachers;

    private Integer periodsToCover;

    private Integer assigned;

    private Integer uncovered;

    private List<SubstitutionResponse> substitutions;
}
