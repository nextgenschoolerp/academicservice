package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A class section of the institution, with how far it is set up for timetabling. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassSectionResponse {
    private UUID sectionId;

    private UUID gradeId;

    private String gradeName;

    private Integer gradeOrder;

    private String sectionName;

    /** "Class 7-A" */
    private String className;

    /** Subjects with a teacher allocated */
    private Integer allocatedSubjects;

    private Integer allocatedPeriodsPerWeek;

    /** Periods placed in the class timetable */
    private Integer timetabledPeriods;
}
