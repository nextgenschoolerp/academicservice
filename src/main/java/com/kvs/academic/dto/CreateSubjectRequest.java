package com.kvs.academic.dto;

import com.kvs.academic.enums.SubjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-06001: a subject in the catalog. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubjectRequest {
    @Size(max = 64)
    private String tenantId;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9_-]{2,20}", message = "must be 2-20 upper-case letters, digits, '_' or '-'")
    private String subjectCode;

    @NotBlank
    @Size(max = 100)
    private String subjectName;

    private SubjectType subjectType;
}
