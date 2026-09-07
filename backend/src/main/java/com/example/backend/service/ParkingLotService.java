package com.example.backend.service;

import com.example.backend.dto.ParkingLotRequest;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.ParkingLotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ParkingLotService {

    private static final String MSG_DELETE_IN_YARD =
            "该停车场仍有在库扣留车辆，请先出库或改为禁用";
    private static final String MSG_NOT_ENABLED =
            "停车场不存在或已禁用";

    @Autowired
    private ParkingLotMapper parkingLotMapper;

    @Autowired
    private DetainedVehicleMapper detainedVehicleMapper;

    public List<ParkingLot> list(String keyword, String status) {
        return parkingLotMapper.findAll().stream()
                .filter(lot -> {
                    if (keyword != null && !keyword.isEmpty()) {
                        String name = lot.getName() != null ? lot.getName() : "";
                        String code = lot.getCode() != null ? lot.getCode() : "";
                        if (!name.contains(keyword) && !code.contains(keyword)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isEmpty()) {
                        if (!status.equals(lot.getStatus())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    public ParkingLot findById(Long id) {
        return parkingLotMapper.findById(id);
    }

    @Transactional
    public boolean create(ParkingLotRequest req) {
        ParkingLot lot = new ParkingLot();
        applyRequest(lot, req, true);
        ensureCodeUnique(lot.getCode(), null);
        return parkingLotMapper.insert(lot) > 0;
    }

    @Transactional
    public boolean update(Long id, ParkingLotRequest req) {
        ParkingLot existing = parkingLotMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("停车场不存在");
        }
        applyRequest(existing, req, false);
        ensureCodeUnique(existing.getCode(), id);
        return parkingLotMapper.update(existing) > 0;
    }

    @Transactional
    public boolean delete(Long id) {
        ParkingLot existing = parkingLotMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("停车场不存在");
        }
        if (detainedVehicleMapper.countInYardByParkingLotId(id) > 0) {
            throw new RuntimeException(MSG_DELETE_IN_YARD);
        }
        return parkingLotMapper.deleteById(id) > 0;
    }

    public ParkingLot requireEnabled(Long id) {
        ParkingLot lot = parkingLotMapper.findById(id);
        if (lot == null || !"ENABLED".equals(lot.getStatus())) {
            throw new RuntimeException(MSG_NOT_ENABLED);
        }
        return lot;
    }

    private void applyRequest(ParkingLot lot, ParkingLotRequest req, boolean creating) {
        if (req.getName() != null) {
            lot.setName(req.getName());
        }
        if (req.getCode() != null) {
            lot.setCode(req.getCode());
        }
        if (req.getAddress() != null) {
            lot.setAddress(req.getAddress());
        }
        if (req.getContactName() != null) {
            lot.setContactName(req.getContactName());
        }
        if (req.getContactPhone() != null) {
            lot.setContactPhone(req.getContactPhone());
        }
        String status = req.getStatus();
        if (status == null || status.isEmpty()) {
            if (creating) {
                lot.setStatus("ENABLED");
            }
        } else {
            if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
                throw new RuntimeException("停车场状态仅允许 ENABLED 或 DISABLED");
            }
            lot.setStatus(status);
        }
        if (req.getRemark() != null) {
            lot.setRemark(req.getRemark());
        }
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        ParkingLot byCode = parkingLotMapper.findByCode(code);
        if (byCode != null && (excludeId == null || !byCode.getId().equals(excludeId))) {
            throw new RuntimeException("停车场编码已存在");
        }
    }
}
