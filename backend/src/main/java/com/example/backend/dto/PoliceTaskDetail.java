package com.example.backend.dto;

import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchOrderEvaluation;
import lombok.Data;

import java.util.List;

@Data
public class PoliceTaskDetail {
    private DispatchOrder order;
    private DispatchFieldRecord fieldRecord;
    private List<DispatchMedia> medias;
    private DispatchOrderEvaluation evaluation;
}
