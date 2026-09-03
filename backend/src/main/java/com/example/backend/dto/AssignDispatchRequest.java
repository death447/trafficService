package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignDispatchRequest {
    @NotNull
    private Long vehicleId;
    private Long rescuerId;
}
