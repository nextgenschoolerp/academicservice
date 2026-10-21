package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.util.UUID;

/** One timetable period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableEntryResponse {
    private UUID timetableId;

    private Integer dayOfWeek;

    private String dayName;

    private Integer periodNumber;

    private LocalTime startTime;

    private LocalTime endTime;

    private UUID sectionId;

    private String className;

    private UUID subjectId;

    private String subjectCode;

    private String subjectName;

    private String teacherId;

    private String teacherName;
}
