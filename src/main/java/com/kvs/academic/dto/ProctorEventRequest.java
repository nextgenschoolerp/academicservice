package com.kvs.academic.dto;

import com.kvs.academic.enums.ProctorEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-06007: a browser event (tab switch, blur, fullscreen exit, copy/paste). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProctorEventRequest {
    @NotNull
    private ProctorEventType eventType;

    @Size(max = 255)
    private String detail;
}
