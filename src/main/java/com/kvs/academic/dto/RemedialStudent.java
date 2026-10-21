package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A student in a remedial session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemedialStudent {
    private UUID studentId;

    private String studentName;

    private String className;
}
