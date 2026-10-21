package com.kvs.academic.dto;

import com.kvs.academic.enums.RemedialStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** A remedial session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemedialSessionResponse {
    private UUID remedialSessionId;

    private UUID subjectId;

    private String subjectCode;

    private UUID gradeId;

    private String teacherId;

    private String teacherName;

    private LocalDate sessionDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String venue;

    private String focusTopic;

    private RemedialStatus status;

    private List<RemedialStudent> students;
}
