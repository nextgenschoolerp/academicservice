package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** US-06016 run result. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverdueAlertRunResponse {
    private Integer assignmentsChecked;

    private Integer alertsCreated;

    private Integer alreadyAlerted;

    private List<HomeworkAlertResponse> alerts;
}
