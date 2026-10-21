package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** A section's weekly timetable (US-06002). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassTimetableResponse {
    private UUID sectionId;

    private String className;

    private UUID bellScheduleId;

    private Integer periodsPerWeek;

    private List<TimetableEntryResponse> entries;
}
