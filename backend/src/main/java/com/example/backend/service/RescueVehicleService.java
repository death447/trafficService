package com.example.backend.service;

import com.example.backend.dto.NearbyVehicleVO;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RescueVehicleService {

    @Autowired
    private RescueVehicleMapper vehicleMapper;

    @Autowired
    private DispatchOrderMapper dispatchOrderMapper;

    public RescueVehicle findById(Long id) {
        return vehicleMapper.findById(id);
    }

    public List<RescueVehicle> findAll() {
        return vehicleMapper.findAll();
    }

    public List<RescueVehicle> list(String keyword, String status, String vehicleType) {
        List<RescueVehicle> vehicles = vehicleMapper.findAll();
        return vehicles.stream()
                .filter(v -> {
                    if (keyword != null && !keyword.isEmpty()) {
                        String plate = v.getPlateNo() != null ? v.getPlateNo() : "";
                        if (!plate.contains(keyword)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isEmpty()) {
                        if (!status.equals(v.getStatus())) {
                            return false;
                        }
                    }
                    if (vehicleType != null && !vehicleType.isEmpty()) {
                        if (!vehicleType.equals(v.getVehicleType())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean createVehicle(RescueVehicle vehicle) {
        if (vehicleMapper.findByPlateNo(vehicle.getPlateNo()) != null) {
            throw new RuntimeException("车牌号已存在");
        }
        if (vehicle.getStatus() == null || vehicle.getStatus().isEmpty()) {
            vehicle.setStatus("IDLE");
        }
        return vehicleMapper.insert(vehicle) > 0;
    }

    @Transactional
    public boolean updateVehicle(RescueVehicle vehicle) {
        RescueVehicle existingByPlate = vehicleMapper.findByPlateNo(vehicle.getPlateNo());
        if (existingByPlate != null && !existingByPlate.getId().equals(vehicle.getId())) {
            throw new RuntimeException("车牌号已存在");
        }
        return vehicleMapper.update(vehicle) > 0;
    }

    @Transactional
    public boolean deleteVehicle(Long id) {
        int activeCount = dispatchOrderMapper.countActiveByVehicleId(id);
        if (activeCount > 0) {
            throw new RuntimeException("该车辆有进行中的工单，无法删除");
        }
        return vehicleMapper.deleteById(id) > 0;
    }

    public List<NearbyVehicleVO> findNearby(BigDecimal lng, BigDecimal lat, Integer limit) {
        int effectiveLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 50);
        double originLng = lng.doubleValue();
        double originLat = lat.doubleValue();

        return vehicleMapper.findByStatus("IDLE").stream()
                .filter(v -> v.getLongitude() != null && v.getLatitude() != null)
                .map(v -> {
                    NearbyVehicleVO vo = new NearbyVehicleVO();
                    vo.setVehicle(v);
                    vo.setDistanceMeters(haversineMeters(
                            originLng, originLat,
                            v.getLongitude().doubleValue(),
                            v.getLatitude().doubleValue()));
                    return vo;
                })
                .sorted(Comparator.comparingDouble(NearbyVehicleVO::getDistanceMeters))
                .limit(effectiveLimit)
                .collect(Collectors.toList());
    }

    public void markBusy(Long id) {
        updateStatus(id, "BUSY");
    }

    public void markIdle(Long id) {
        updateStatus(id, "IDLE");
    }

    public void updateStatus(Long id, String status) {
        vehicleMapper.updateStatus(id, status);
    }

    private static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}
