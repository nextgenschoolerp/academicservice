package com.kvs.academic.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-06003: choose another proxy, or cancel the substitution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignProxyRequest {
    @Size(max = 64)
    private String proxyTeacherId;

    private Boolean cancel;
}
