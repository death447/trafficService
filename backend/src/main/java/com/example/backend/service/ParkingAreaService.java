package com.example.backend.service;

import com.example.backend.dto.ParkingAreaRequest;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.ParkingAreaMapper;
import com.example.backend.mapper.ParkingLotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ParkingAreaService {

    @Autowired
    private ParkingAreaMapper parkingAreaMapper;

    @Autowired
    private ParkingLotMapper parkingLotMapper;

    @Autowired
    private DetainedVehicleMapper detainedVehicleMapper;

    public List<ParkingArea> list(Long parkingLotId, String status) {
        requireLot(parkingLotId);
        return parkingAreaMapper.findByParkingLotId(parkingLotId, status);
    }

    public ParkingArea findById(Long id) {
        return parkingAreaMapper.findById(id);
    }

    @Transactional
    public ParkingArea create(Long parkingLotId, ParkingAreaRequest req) {
        requireLot(parkingLotId);
        ParkingArea area = new ParkingArea();
        area.setParkingLotId(parkingLotId);
        apply(area, req, true);
        ensureNameUnique(parkingLotId, area.getName(), null);
        if (parkingAreaMapper.insert(area) <= 0) {
            throw new RuntimeException("创建停放区域失败");
        }
        return area;
    }

    @Transactional
    public boolean update(Long id, ParkingAreaRequest req) {
        ParkingArea existing = parkingAreaMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("停放区域不存在");
        }
        apply(existing, req, false);
        ensureNameUnique(existing.getParkingLotId(), existing.getName(), id);
        return parkingAreaMapper.update(existing) > 0;
    }

    @Transactional
    public boolean delete(Long id) {
        ParkingArea existing = parkingAreaMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("停放区域不存在");
        }
        if (detainedVehicleMapper.countByParkingAreaId(id) > 0) {
            throw new RuntimeException("该区域仍有扣留车辆，请先停用");
        }
        return parkingAreaMapper.deleteById(id) > 0;
    }

    public ParkingArea requireEnabledForLot(Long areaId, Long parkingLotId) {
        ParkingArea area = parkingAreaMapper.findById(areaId);
        if (area == null || !"ENABLED".equals(area.getStatus())) {
            throw new RuntimeException("停放区域不存在或已停用");
        }
        if (!parkingLotId.equals(area.getParkingLotId())) {
            throw new RuntimeException("停放区域不属于所选停车场");
        }
        return area;
    }

    private ParkingLot requireLot(Long parkingLotId) {
        if (parkingLotId == null) {
            throw new RuntimeException("停车场不能为空");
        }
        ParkingLot lot = parkingLotMapper.findById(parkingLotId);
        if (lot == null) {
            throw new RuntimeException("停车场不存在");
        }
        return lot;
    }

    private void apply(ParkingArea area, ParkingAreaRequest req, boolean creating) {
        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty()) {
                throw new RuntimeException("区域名称不能为空");
            }
            area.setName(name);
        } else if (creating) {
            throw new RuntimeException("区域名称不能为空");
        }
        String status = req.getStatus();
        if (status == null || status.isEmpty()) {
            if (creating) {
                area.setStatus("ENABLED");
            }
        } else {
            if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
                throw new RuntimeException("区域状态仅允许 ENABLED 或 DISABLED");
            }
            area.setStatus(status);
        }
    }

    private void ensureNameUnique(Long lotId, String name, Long excludeId) {
        ParkingArea byName = parkingAreaMapper.findByLotIdAndName(lotId, name);
        if (byName != null && (excludeId == null || !byName.getId().equals(excludeId))) {
            throw new RuntimeException("该停车场已存在同名区域");
        }
    }
}
