package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** A teacher's weekly timetable (US-06002, US-06015). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherTimetableResponse {
    private String teacherId;

    private String teacherName;

    private Integer periodsPerWeek;

    private Integer weeklyMaxPeriods;

    private List<TimetableEntryResponse> entries;
}
