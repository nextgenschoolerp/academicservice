package com.kvs.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** US-06018: playback progress reported by the parent app. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchProgressRequest {
    @NotNull
    private UUID studentId;

    @NotNull
    @Min(0)
    private Integer positionSeconds;

    @Min(0)
    @Max(3600)
    private Integer watchedSeconds;
}
