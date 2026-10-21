package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** US-06006 generator result; the questions are added to the draft quiz. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGenerationResponse {
    private Integer requested;

    private Integer generated;

    private Integer sentencesScanned;

    private List<QuestionResponse> questions;
}
