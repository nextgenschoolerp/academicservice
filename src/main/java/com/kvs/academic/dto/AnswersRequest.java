package com.kvs.academic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** US-06007: autosave or final answers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswersRequest {
    @NotNull
    @Size(max = 200)
    @Valid
    private List<AnswerItem> answers;
}
