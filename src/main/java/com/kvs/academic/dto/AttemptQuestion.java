package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** A question as shown to the student: options in display order, no answer key. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptQuestion {
    private UUID questionId;

    private Integer number;

    private String questionText;

    private List<String> options;

    private Integer marks;

    private Integer selectedOption;
}
