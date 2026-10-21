package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** Generator outcome for one section (US-06002). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionGenerationResult {
    private UUID sectionId;

    private String className;

    private Integer periodsRequired;

    private Integer periodsPlaced;

    private Integer freePeriods;

    private List<UnplacedPeriods> unplaced;
}
