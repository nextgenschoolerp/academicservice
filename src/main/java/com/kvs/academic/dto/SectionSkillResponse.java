package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** US-06009: average topic mastery of a section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionSkillResponse {
    private UUID sectionId;

    private String className;

    private Integer students;

    private List<SkillAxis> topics;
}
