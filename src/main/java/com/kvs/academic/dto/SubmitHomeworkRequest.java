package com.kvs.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** US-06010: a student's submission (text and/or a link to the uploaded work). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitHomeworkRequest {
    @NotNull
    private UUID studentId;

    @Size(max = 4000)
    private String submissionText;

    @Size(max = 500)
    private String attachmentUrl;
}
