package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** US-06019 archive result. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveResultResponse {
    private UUID academicYearId;

    private String sessionName;

    private Integer lessonPlansArchived;

    private Integer syllabusTopicsArchived;

    private Instant archivedAt;
}
