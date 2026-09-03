package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AbortDispatchRequest {
    @NotBlank
    private String abortReason;
}
