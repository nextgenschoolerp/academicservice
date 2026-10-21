package com.kvs.academic.dto;

import com.kvs.academic.enums.SubjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A catalog subject. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResponse {
    private UUID subjectId;

    private String subjectCode;

    private String subjectName;

    private SubjectType subjectType;
}
