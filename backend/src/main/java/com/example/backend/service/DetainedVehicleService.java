package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.DetainInRequest;
import com.example.backend.dto.DetainUpdateRequest;
import com.example.backend.entity.DetainMedia;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainMediaMapper;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.ParkingLotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DetainedVehicleService {

    private static final Set<String> MEDIA_BIZ_TYPES = Set.of("SCENE", "PARK");
    private static final Set<String> RESCUE_REASONS = Set.of("ACCIDENT", "ILLEGAL", "RESCUE");
    private static final Set<String> HAS_KEY_VALUES = Set.of("YES", "NO");

    @Autowired
    private DetainedVehicleMapper detainedVehicleMapper;

    @Autowired
    private ParkingLotService parkingLotService;

    @Autowired
    private ParkingLotMapper parkingLotMapper;

    @Autowired
    private ParkingAreaService parkingAreaService;

    @Autowired
    private DispatchOrderMapper dispatchOrderMapper;

    @Autowired
    private DetainMediaMapper detainMediaMapper;

    @Autowired
    private LocalFileStorageService fileStorageService;

    public Map<String, Object> list(String plateNo, String detainNo, String status,
                                  Long parkingLotId, String detainDept, PageParams pp) {
        long total = detainedVehicleMapper.count(plateNo, detainNo, status, parkingLotId, detainDept);
        List<DetainedVehicle> vehicles = detainedVehicleMapper.selectPage(
                plateNo, detainNo, status, parkingLotId, detainDept, pp.getOffset(), pp.getSize());
        vehicles.forEach(this::enrich);
        return pp.toResult(vehicles, total);
    }

    public DetainedVehicle findById(Long id) {
        DetainedVehicle v = detainedVehicleMapper.findById(id);
        if (v != null) {
            enrich(v);
        }
        return v;
    }

    @Transactional
    public DetainedVehicle checkIn(DetainInRequest req, Long operatorUserId) {
        String plate = requireTrimmedPlate(req.getPlateNo());
        String detainNo = requireDetainNo(req.getDetainNo());
        if (req.getParkingLotId() == null) {
            throw new RuntimeException("停车场不能为空");
        }
        parkingLotService.requireEnabled(req.getParkingLotId());
        if (detainedVehicleMapper.findByDetainNo(detainNo) != null) {
            throw new RuntimeException("扣押编号已存在");
        }
        if (detainedVehicleMapper.countInYardByPlateNo(plate) > 0) {
            throw new RuntimeException("该车牌已有在库扣留车辆");
        }
        if (req.getDispatchOrderId() != null) {
            DispatchOrder order = dispatchOrderMapper.findById(req.getDispatchOrderId());
            if (order == null) {
                throw new RuntimeException("关联救援工单不存在");
            }
        }
        validateArea(req.getParkingAreaId(), req.getParkingLotId());
        validateEnums(req.getHasKey(), req.getRescueReason());

        DetainedVehicle v = new DetainedVehicle();
        v.setDetainNo(detainNo);
        v.setEntryNo(generateEntryNo());
        v.setPlateNo(plate);
        v.setParkingLotId(req.getParkingLotId());
        v.setDispatchOrderId(req.getDispatchOrderId());
        applyExtended(v, req.getVehicleType(), req.getBrandModel(), req.getVehicleColor(), req.getMileage(),
                req.getImportantEquipment(), req.getHasKey(), req.getParkingAreaId(), req.getStallNo(),
                req.getDetainDept(), req.getRescuerName(), req.getRescueReason(), req.getRescueMethod(),
                req.getRescueTime(), req.getRescueAddress(), req.getRemark());
        v.setStatus("IN_YARD");
        v.setInTime(LocalDateTime.now());
        v.setOperatorInId(operatorUserId);
        if (detainedVehicleMapper.insert(v) <= 0) {
            throw new RuntimeException("入库失败");
        }
        return v;
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
        if (!req.getParkingLotId().equals(existing.getParkingLotId())) {
            parkingLotService.requireEnabled(req.getParkingLotId());
        }
        if (detainedVehicleMapper.countInYardByPlateNoExcludingId(plate, id) > 0) {
            throw new RuntimeException("该车牌已有在库扣留车辆");
        }
        if (req.getDispatchOrderId() != null) {
            DispatchOrder order = dispatchOrderMapper.findById(req.getDispatchOrderId());
            if (order == null) {
                throw new RuntimeException("关联救援工单不存在");
            }
        }
        validateArea(req.getParkingAreaId(), req.getParkingLotId());
        validateEnums(req.getHasKey(), req.getRescueReason());

        existing.setPlateNo(plate);
        existing.setParkingLotId(req.getParkingLotId());
        existing.setDispatchOrderId(req.getDispatchOrderId());
        applyExtended(existing, req.getVehicleType(), req.getBrandModel(), req.getVehicleColor(), req.getMileage(),
                req.getImportantEquipment(), req.getHasKey(), req.getParkingAreaId(), req.getStallNo(),
                req.getDetainDept(), req.getRescuerName(), req.getRescueReason(), req.getRescueMethod(),
                req.getRescueTime(), req.getRescueAddress(), req.getRemark());
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

    public List<DetainMedia> listMedia(Long detainId) {
        requireExisting(detainId);
        return detainMediaMapper.findByDetainId(detainId);
    }

    @Transactional
    public DetainMedia addMedia(Long detainId, String bizType, MultipartFile file, Long userId) {
        requireExisting(detainId);
        if (bizType == null || !MEDIA_BIZ_TYPES.contains(bizType)) {
            throw new RuntimeException("媒体类型无效，仅支持 SCENE/PARK");
        }
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请上传文件");
        }
        String relativePath;
        try {
            relativePath = fileStorageService.storeDetainMedia(detainId, file);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败");
        }
        DetainMedia media = new DetainMedia();
        media.setDetainId(detainId);
        media.setBizType(bizType);
        media.setFilePath(relativePath);
        media.setSortOrder(0);
        media.setUploadedBy(userId);
        detainMediaMapper.insert(media);
        return media;
    }

    @Transactional
    public void deleteMedia(Long detainId, Long mediaId) {
        requireExisting(detainId);
        DetainMedia media = detainMediaMapper.findById(mediaId);
        if (media == null || !detainId.equals(media.getDetainId())) {
            throw new RuntimeException("照片不存在");
        }
        if (media.getFilePath() != null) {
            fileStorageService.deleteDispatchMedia(media.getFilePath());
        }
        detainMediaMapper.deleteById(mediaId);
    }

    private DetainedVehicle requireExisting(Long id) {
        DetainedVehicle v = detainedVehicleMapper.findById(id);
        if (v == null) {
            throw new RuntimeException("扣留车辆不存在");
        }
        return v;
    }

    private String generateEntryNo() {
        String prefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = detainedVehicleMapper.countByEntryNoPrefix(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    private String requireTrimmedPlate(String plateNo) {
        if (plateNo == null || plateNo.trim().isEmpty()) {
            throw new RuntimeException("车牌不能为空");
        }
        return plateNo.trim();
    }

    private String requireDetainNo(String detainNo) {
        if (detainNo == null || detainNo.trim().isEmpty()) {
            throw new RuntimeException("扣押编号不能为空");
        }
        return detainNo.trim();
    }

    private void validateArea(Long parkingAreaId, Long parkingLotId) {
        if (parkingAreaId == null) {
            return;
        }
        parkingAreaService.requireEnabledForLot(parkingAreaId, parkingLotId);
    }

    private void validateEnums(String hasKey, String rescueReason) {
        if (hasKey != null && !hasKey.isBlank() && !HAS_KEY_VALUES.contains(hasKey)) {
            throw new RuntimeException("有无钥匙仅允许 YES 或 NO");
        }
        if (rescueReason != null && !rescueReason.isBlank() && !RESCUE_REASONS.contains(rescueReason)) {
            throw new RuntimeException("施救原因无效");
        }
    }

    private void applyExtended(DetainedVehicle v, String vehicleType, String brandModel, String vehicleColor,
                               String mileage, String importantEquipment, String hasKey, Long parkingAreaId,
                               String stallNo, String detainDept, String rescuerName, String rescueReason,
                               String rescueMethod, LocalDateTime rescueTime, String rescueAddress, String remark) {
        v.setVehicleType(emptyToNull(vehicleType));
        v.setBrandModel(emptyToNull(brandModel));
        v.setVehicleColor(emptyToNull(vehicleColor));
        v.setMileage(emptyToNull(mileage));
        v.setImportantEquipment(emptyToNull(importantEquipment));
        v.setHasKey(emptyToNull(hasKey));
        v.setParkingAreaId(parkingAreaId);
        v.setStallNo(emptyToNull(stallNo));
        v.setDetainDept(emptyToNull(detainDept));
        v.setRescuerName(emptyToNull(rescuerName));
        v.setRescueReason(emptyToNull(rescueReason));
        v.setRescueMethod(emptyToNull(rescueMethod));
        v.setRescueTime(rescueTime);
        v.setRescueAddress(emptyToNull(rescueAddress));
        v.setRemark(emptyToNull(remark));
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void enrich(DetainedVehicle v) {
        if (v.getParkingLotId() != null) {
            ParkingLot lot = parkingLotMapper.findById(v.getParkingLotId());
            if (lot != null) {
                v.setParkingLotName(lot.getName());
            }
        }
        if (v.getParkingAreaId() != null) {
            ParkingArea area = parkingAreaService.findById(v.getParkingAreaId());
            if (area != null) {
                v.setParkingAreaName(area.getName());
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
