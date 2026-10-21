package com.kvs.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A teacher's annotation on a submission (US-06008). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Annotation {
    @NotBlank
    @Size(max = 100)
    private String anchor;

    @NotBlank
    @Size(max = 500)
    private String comment;
}
