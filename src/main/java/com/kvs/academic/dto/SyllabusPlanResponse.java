package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** An annual syllabus plan. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusPlanResponse {
    private UUID academicYearId;

    private UUID gradeId;

    private String gradeName;

    private UUID subjectId;

    private String subjectCode;

    private String subjectName;

    private List<SyllabusTopicResponse> topics;
}
