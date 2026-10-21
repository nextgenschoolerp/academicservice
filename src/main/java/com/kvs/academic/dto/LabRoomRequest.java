package com.kvs.academic.dto;

import com.kvs.academic.enums.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-06011: a bookable lab. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabRoomRequest {
    @Size(max = 64)
    private String tenantId;

    @NotBlank
    @Size(max = 64)
    private String branchId;

    @NotBlank
    @Size(max = 100)
    private String roomName;

    @NotNull
    private RoomType roomType;

    @NotNull
    @Min(1)
    @Max(200)
    private Integer capacity;
}
