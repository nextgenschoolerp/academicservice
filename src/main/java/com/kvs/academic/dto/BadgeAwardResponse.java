package com.kvs.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** US-06017 award result. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeAwardResponse {
    private List<BadgeResponse> awarded;

    private Integer alreadyHeld;
}
