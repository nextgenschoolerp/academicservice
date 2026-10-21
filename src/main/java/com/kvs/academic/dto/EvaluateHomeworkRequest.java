package com.kvs.academic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/** US-06008: marks, feedback and annotations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateHomeworkRequest {
    @NotNull
    @DecimalMin("0")
    private BigDecimal marksAwarded;

    @Size(max = 2000)
    private String feedback;

    @Size(max = 50)
    @Valid
    private List<Annotation> annotations;
}
