package com.example.backend.dto;

import com.example.backend.entity.DispatchOrder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchOverview {
    private long todayTotal;
    private long todayDispatched;
    private long todayCompleted;
    private long inProgress;
    private List<DispatchOrder> accidents = new ArrayList<>();
}
