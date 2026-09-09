package com.example.backend.service;

import com.example.backend.dto.AssignedVehicleSnapshot;
import com.example.backend.dto.BindVehicleRequest;
import com.example.backend.dto.LocationReportRequest;
import com.example.backend.dto.LocationReportResponse;
import com.example.backend.dto.ParkRequest;
import com.example.backend.dto.RescuerProfileUpdateRequest;
import com.example.backend.dto.RescuerTaskDetail;
import com.example.backend.dto.SceneRequest;
import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.User;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RescuerMobileService {

    private static final Pattern RV_PAYLOAD = Pattern.compile("^RV:(\\d+)$");
    private static final Set<String> MEDIA_BIZ_TYPES = Set.of("DAMAGE", "PARK");

    @Autowired
    private DispatchOrderMapper dispatchOrderMapper;

    @Autowired
    private DispatchFieldRecordMapper fieldRecordMapper;

    @Autowired
    private DispatchMediaMapper mediaMapper;

    @Autowired
    private RescueVehicleMapper rescueVehicleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LocalFileStorageService fileStorageService;

    public List<DispatchOrder> listTasks(Long userId, String tab) {
        Set<String> statuses;
        if ("todo".equals(tab)) {
            statuses = Set.of("DISPATCHED", "ACCEPTED");
        } else if ("done".equals(tab)) {
            statuses = Set.of("COMPLETED");
        } else if ("aborted".equals(tab)) {
            statuses = Set.of("ABORTED");
        } else {
            throw new RuntimeException("无效的任务分类");
        }
        return dispatchOrderMapper.findAll().stream()
                .filter(o -> userId.equals(o.getRescuerId()))
                .filter(o -> statuses.contains(o.getStatus()))
                .collect(Collectors.toList());
    }

    public RescuerTaskDetail getTask(Long userId, Long orderId) {
        DispatchOrder order = requireOwned(orderId, userId);
        RescuerTaskDetail detail = new RescuerTaskDetail();
        detail.setOrder(order);
        detail.setFieldRecord(fieldRecordMapper.findByOrderId(orderId));
        if (order.getVehicleId() != null) {
            RescueVehicle vehicle = rescueVehicleMapper.findById(order.getVehicleId());
            if (vehicle != null) {
                AssignedVehicleSnapshot snap = new AssignedVehicleSnapshot();
                snap.setId(vehicle.getId());
                snap.setPlateNo(vehicle.getPlateNo());
                snap.setLongitude(vehicle.getLongitude());
                snap.setLatitude(vehicle.getLatitude());
                snap.setLocationUpdatedAt(vehicle.getLocationUpdatedAt());
                detail.setAssignedVehicle(snap);
            }
        }
        return detail;
    }

    @Transactional
    public void saveScene(Long userId, Long orderId, SceneRequest req) {
        requireOwnedAccepted(orderId, userId);
        DispatchFieldRecord record = getOrCreateRecord(orderId);
        if (req != null) {
            record.setPlateNo(req.getPlateNo());
            record.setVehicleType(req.getVehicleType());
            record.setDamageDesc(req.getDamageDesc());
            record.setSceneRemark(req.getSceneRemark());
        }
        record.setSceneSubmittedAt(LocalDateTime.now());
        upsert(record);
    }

    @Transactional
    public void savePark(Long userId, Long orderId, ParkRequest req) {
        requireOwnedAccepted(orderId, userId);
        DispatchFieldRecord record = getOrCreateRecord(orderId);
        if (req != null) {
            record.setParkAddress(req.getParkAddress());
            record.setParkRemark(req.getParkRemark());
        }
        record.setParkSubmittedAt(LocalDateTime.now());
        upsert(record);
    }

    @Transactional
    public DispatchMedia addMedia(Long userId, Long orderId, String bizType, MultipartFile file) {
        requireOwnedAccepted(orderId, userId);
        if (bizType == null || !MEDIA_BIZ_TYPES.contains(bizType)) {
            throw new RuntimeException("媒体类型无效，仅支持 DAMAGE/PARK");
        }
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请上传文件");
        }
        String relativePath;
        try {
            relativePath = fileStorageService.storeDispatchMedia(orderId, file);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败");
        }
        DispatchMedia media = new DispatchMedia();
        media.setDispatchOrderId(orderId);
        media.setBizType(bizType);
        media.setFilePath(relativePath);
        media.setSortOrder(0);
        media.setUploadedBy(userId);
        mediaMapper.insert(media);
        return media;
    }

    public List<DispatchMedia> listMedia(Long userId, Long orderId) {
        requireOwned(orderId, userId);
        return mediaMapper.findByOrderId(orderId);
    }

    @Transactional
    public void updateProfile(Long userId, RescuerProfileUpdateRequest req) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (req != null) {
            if (req.getRealName() != null) {
                user.setRealName(req.getRealName());
            }
            if (req.getPhone() != null) {
                user.setPhone(req.getPhone());
            }
            if (req.getEmail() != null) {
                user.setEmail(req.getEmail());
            }
        }
        userMapper.update(user);
    }

    @Transactional
    public RescueVehicle bindVehicle(Long userId, BindVehicleRequest req) {
        if (req == null || req.getQrPayload() == null || req.getQrPayload().isBlank()) {
            throw new RuntimeException("二维码内容无效");
        }
        Matcher matcher = RV_PAYLOAD.matcher(req.getQrPayload().trim());
        if (!matcher.matches()) {
            throw new RuntimeException("二维码格式无效，应为 RV:{车辆ID}");
        }
        Long vehicleId = Long.parseLong(matcher.group(1));
        RescueVehicle vehicle = rescueVehicleMapper.findById(vehicleId);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }

        RescueVehicle previous = rescueVehicleMapper.findByDriverUserId(userId);
        if (previous != null && !previous.getId().equals(vehicleId)) {
            previous.setDriverUserId(null);
            if (!"BUSY".equals(previous.getStatus())) {
                previous.setStatus("OFFLINE");
            }
            rescueVehicleMapper.update(previous);
        } else {
            rescueVehicleMapper.clearDriverByUserId(userId);
        }

        vehicle.setDriverUserId(userId);
        // 扫码绑定后上线：非忙碌车辆置为空闲
        if (!"BUSY".equals(vehicle.getStatus())) {
            vehicle.setStatus("IDLE");
        }
        rescueVehicleMapper.update(vehicle);
        return vehicle;
    }

    public RescueVehicle getBoundVehicle(Long userId) {
        return rescueVehicleMapper.findByDriverUserId(userId);
    }

    @Transactional
    public LocationReportResponse reportLocation(Long userId, LocationReportRequest request) {
        if (request == null || request.getLng() == null || request.getLat() == null) {
            throw new RuntimeException("经纬度不能为空");
        }
        double lng = request.getLng().doubleValue();
        double lat = request.getLat().doubleValue();
        if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
            throw new RuntimeException("经纬度无效");
        }
        RescueVehicle vehicle = rescueVehicleMapper.findByDriverUserId(userId);
        if (vehicle == null) {
            throw new RuntimeException("请先绑定车辆");
        }
        LocalDateTime now = LocalDateTime.now();
        rescueVehicleMapper.updateLocation(vehicle.getId(), request.getLng(), request.getLat(), now);
        LocationReportResponse resp = new LocationReportResponse();
        resp.setVehicleId(vehicle.getId());
        resp.setLocationUpdatedAt(now);
        return resp;
    }

    private DispatchOrder requireOwned(Long orderId, Long userId) {
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        if (userId == null || !userId.equals(order.getRescuerId())) {
            throw new RuntimeException("无权操作该工单");
        }
        return order;
    }

    private DispatchOrder requireOwnedAccepted(Long orderId, Long userId) {
        DispatchOrder order = requireOwned(orderId, userId);
        if (!"ACCEPTED".equals(order.getStatus())) {
            throw new RuntimeException("仅已接单状态可操作");
        }
        return order;
    }

    private DispatchFieldRecord getOrCreateRecord(Long orderId) {
        DispatchFieldRecord existing = fieldRecordMapper.findByOrderId(orderId);
        if (existing != null) {
            return existing;
        }
        DispatchFieldRecord created = new DispatchFieldRecord();
        created.setDispatchOrderId(orderId);
        return created;
    }

    private void upsert(DispatchFieldRecord record) {
        if (record.getId() == null) {
            fieldRecordMapper.insert(record);
        } else {
            fieldRecordMapper.update(record);
        }
    }
}
