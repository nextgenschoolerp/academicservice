package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** US-06008: who submitted and who was evaluated. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkRosterResponse {
    private HomeworkResponse homework;

    private List<HomeworkRosterRow> rows;
}
