package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/** US-06002 generator report. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableGenerationResponse {
    private UUID bellScheduleId;

    private Integer sectionsScheduled;

    private Integer periodsRequired;

    private Integer periodsPlaced;

    private Boolean conflictFree;

    private List<SectionGenerationResult> sections;

    /** Proxy substitutions from today on that pointed at the replaced periods; run auto-assign again for those days. */
    private Integer substitutionsCleared;
}
