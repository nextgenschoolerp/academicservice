package com.kvs.academic.dto;

import com.kvs.academic.enums.QuestionSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** A question of the quiz's bank (teacher view, with the answer). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {
    private UUID questionId;

    private Integer sequenceNo;

    private String questionText;

    private List<String> options;

    private Integer correctOptionIndex;

    private String topic;

    private Integer marks;

    private QuestionSource source;

    private String explanation;
}
