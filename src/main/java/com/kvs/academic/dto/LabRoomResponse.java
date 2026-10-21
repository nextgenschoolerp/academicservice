package com.kvs.academic.dto;

import com.kvs.academic.enums.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** A lab room. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabRoomResponse {
    private UUID roomId;

    private String branchId;

    private String roomName;

    private RoomType roomType;

    private Integer capacity;

    private Boolean active;
}
