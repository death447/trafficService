package com.example.backend.service;

import com.example.backend.dto.DetainInRequest;
import com.example.backend.dto.DetainUpdateRequest;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.ParkingLotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DetainedVehicleService {

    @Autowired
    private DetainedVehicleMapper detainedVehicleMapper;

    @Autowired
    private ParkingLotService parkingLotService;

    @Autowired
    private ParkingLotMapper parkingLotMapper;

    @Autowired
    private DispatchOrderMapper dispatchOrderMapper;

    public List<DetainedVehicle> list(String plateNo, String detainNo, String status,
                                      Long parkingLotId, String detainDept) {
        return detainedVehicleMapper.findAll().stream()
                .filter(v -> {
                    if (plateNo != null && !plateNo.isEmpty()) {
                        String plate = v.getPlateNo() != null ? v.getPlateNo() : "";
                        if (!plate.contains(plateNo)) {
                            return false;
                        }
                    }
                    if (detainNo != null && !detainNo.isEmpty()) {
                        String no = v.getDetainNo() != null ? v.getDetainNo() : "";
                        if (!no.contains(detainNo)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isEmpty()) {
                        if (!status.equals(v.getStatus())) {
                            return false;
                        }
                    }
                    if (parkingLotId != null) {
                        if (v.getParkingLotId() == null || !parkingLotId.equals(v.getParkingLotId())) {
                            return false;
                        }
                    }
                    if (detainDept != null && !detainDept.isEmpty()) {
                        String dept = v.getDetainDept() != null ? v.getDetainDept() : "";
                        if (!dept.contains(detainDept)) {
                            return false;
                        }
                    }
                    return true;
                })
                .peek(this::enrich)
                .collect(Collectors.toList());
    }

    public DetainedVehicle findById(Long id) {
        DetainedVehicle v = detainedVehicleMapper.findById(id);
        if (v != null) {
            enrich(v);
        }
        return v;
    }

    @Transactional
    public boolean checkIn(DetainInRequest req, Long operatorUserId) {
        String plate = requireTrimmedPlate(req.getPlateNo());
        if (req.getParkingLotId() == null) {
            throw new RuntimeException("停车场不能为空");
        }
        parkingLotService.requireEnabled(req.getParkingLotId());
        if (detainedVehicleMapper.countInYardByPlateNo(plate) > 0) {
            throw new RuntimeException("该车牌已有在库扣留车辆");
        }
        if (req.getDispatchOrderId() != null) {
            DispatchOrder order = dispatchOrderMapper.findById(req.getDispatchOrderId());
            if (order == null) {
                throw new RuntimeException("关联救援工单不存在");
            }
        }

        DetainedVehicle v = new DetainedVehicle();
        v.setDetainNo(generateDetainNo());
        v.setPlateNo(plate);
        v.setVehicleType(req.getVehicleType());
        v.setParkingLotId(req.getParkingLotId());
        v.setDispatchOrderId(req.getDispatchOrderId());
        v.setDetainDept(req.getDetainDept());
        v.setStatus("IN_YARD");
        v.setInTime(LocalDateTime.now());
        v.setOperatorInId(operatorUserId);
        v.setRemark(req.getRemark());
        return detainedVehicleMapper.insert(v) > 0;
    }

    @Transactional
    public boolean update(Long id, DetainUpdateRequest req) {
        DetainedVehicle existing = detainedVehicleMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("扣留车辆不存在");
        }
        if (!"IN_YARD".equals(existing.getStatus())) {
            throw new RuntimeException("仅在库状态可编辑");
        }
        String plate = requireTrimmedPlate(req.getPlateNo());
        if (req.getParkingLotId() == null) {
            throw new RuntimeException("停车场不能为空");
        }
        parkingLotService.requireEnabled(req.getParkingLotId());
        if (detainedVehicleMapper.countInYardByPlateNoExcludingId(plate, id) > 0) {
            throw new RuntimeException("该车牌已有在库扣留车辆");
        }
        if (req.getDispatchOrderId() != null) {
            DispatchOrder order = dispatchOrderMapper.findById(req.getDispatchOrderId());
            if (order == null) {
                throw new RuntimeException("关联救援工单不存在");
            }
        }

        existing.setPlateNo(plate);
        existing.setParkingLotId(req.getParkingLotId());
        if (req.getVehicleType() != null) {
            existing.setVehicleType(req.getVehicleType());
        }
        existing.setDispatchOrderId(req.getDispatchOrderId());
        if (req.getDetainDept() != null) {
            existing.setDetainDept(req.getDetainDept());
        }
        if (req.getRemark() != null) {
            existing.setRemark(req.getRemark());
        }
        return detainedVehicleMapper.update(existing) > 0;
    }

    @Transactional
    public boolean checkOut(Long id, Long operatorUserId) {
        DetainedVehicle existing = detainedVehicleMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("扣留车辆不存在");
        }
        if (!"IN_YARD".equals(existing.getStatus())) {
            throw new RuntimeException("仅在库车辆可出库");
        }
        existing.setStatus("OUT");
        existing.setOutTime(LocalDateTime.now());
        existing.setOperatorOutId(operatorUserId);
        return detainedVehicleMapper.update(existing) > 0;
    }

    @Transactional
    public boolean clear(Long id) {
        DetainedVehicle existing = detainedVehicleMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("扣留车辆不存在");
        }
        if (!"OUT".equals(existing.getStatus())) {
            throw new RuntimeException("仅已出库车辆可清理");
        }
        existing.setStatus("CLEARED");
        existing.setClearedAt(LocalDateTime.now());
        return detainedVehicleMapper.update(existing) > 0;
    }

    private String generateDetainNo() {
        String prefix = "DV" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = detainedVehicleMapper.countByDetainNoPrefix(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    private String requireTrimmedPlate(String plateNo) {
        if (plateNo == null || plateNo.trim().isEmpty()) {
            throw new RuntimeException("车牌不能为空");
        }
        return plateNo.trim();
    }

    private void enrich(DetainedVehicle v) {
        if (v.getParkingLotId() != null) {
            ParkingLot lot = parkingLotMapper.findById(v.getParkingLotId());
            if (lot != null) {
                v.setParkingLotName(lot.getName());
            }
        }
        if (v.getDispatchOrderId() != null) {
            DispatchOrder order = dispatchOrderMapper.findById(v.getDispatchOrderId());
            if (order != null) {
                v.setOrderNo(order.getOrderNo());
            }
        }
    }
}
