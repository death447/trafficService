package com.example.backend.dto;

import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchOrder;
import lombok.Data;

@Data
public class RescuerTaskDetail {
    private DispatchOrder order;
    private DispatchFieldRecord fieldRecord;
    private AssignedVehicleSnapshot assignedVehicle;
}
