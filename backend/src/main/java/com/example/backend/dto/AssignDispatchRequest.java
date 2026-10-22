package com.example.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssignDispatchRequest {
    /** Single vehicle (backward compatible). */
    private Long vehicleId;
    /** Optional batch; merged with vehicleId. */
    private List<Long> vehicleIds;
    /** Ignored; rescuer always comes from vehicle binding. */
    private Long rescuerId;
}
