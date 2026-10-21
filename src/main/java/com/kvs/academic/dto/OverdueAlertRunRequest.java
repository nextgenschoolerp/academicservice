package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** US-06016: send overdue alerts (optionally for one section). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverdueAlertRunRequest {
    private UUID sectionId;
}
