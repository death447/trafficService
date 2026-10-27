package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.AssignedVehicleSnapshot;
import com.example.backend.dto.BindVehicleRequest;
import com.example.backend.dto.DetainInRequest;
import com.example.backend.dto.LocationReportRequest;
import com.example.backend.dto.LocationReportResponse;
import com.example.backend.dto.ParkRequest;
import com.example.backend.dto.RescuerProfileUpdateRequest;
import com.example.backend.dto.RescuerTaskDetail;
import com.example.backend.dto.SceneRequest;
import com.example.backend.entity.DetainMedia;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.DispatchAccidentVehicle;
import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchVehicleAssignment;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.User;
import com.example.backend.mapper.DetainMediaMapper;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.DispatchAccidentVehicleMapper;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.DispatchVehicleAssignmentMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import com.example.backend.support.EntryNoGenerator;
import com.example.backend.util.PlateNos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private DispatchVehicleAssignmentMapper assignmentMapper;

    @Autowired
    private DispatchAccidentVehicleMapper accidentVehicleMapper;

    @Autowired
    private DispatchFieldRecordMapper fieldRecordMapper;

    @Autowired
    private DispatchMediaMapper mediaMapper;

    @Autowired
    private DispatchOrderEvaluationMapper evaluationMapper;

    @Autowired
    private RescueVehicleMapper rescueVehicleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LocalFileStorageService fileStorageService;

    @Autowired
    private DetainedVehicleService detainedVehicleService;

    @Autowired
    private DetainedVehicleMapper detainedVehicleMapper;

    @Autowired
    private DetainMediaMapper detainMediaMapper;

    @Autowired
    private ParkingLotService parkingLotService;

    @Autowired
    private ParkingAreaService parkingAreaService;

    @Autowired
    private EntryNoGenerator entryNoGenerator;

    public List<DispatchOrder> listTasks(Long userId, String tab) {
        Set<String> statuses;
        if ("todo".equals(tab)) {
            statuses = Set.of("DISPATCHED", "ACCEPTED");
        } else if ("done".equals(tab)) {
            statuses = Set.of("COMPLETED");
        } else if ("aborted".equals(tab)) {
            statuses = Set.of("ABORTED", "REJECTED");
        } else {
            throw new RuntimeException("无效的任务分类");
        }
        return assignmentMapper.findByRescuerId(userId).stream()
                .filter(a -> statuses.contains(a.getStatus()))
                .map(a -> {
                    DispatchOrder order = dispatchOrderMapper.findById(a.getDispatchOrderId());
                    if (order == null) {
                        return null;
                    }
                    projectAssignmentOntoOrder(order, a);
                    return order;
                })
                .filter(o -> o != null)
                .collect(Collectors.toList());
    }

    public RescuerTaskDetail getTask(Long userId, Long orderId) {
        DispatchVehicleAssignment assignment = requireAssignment(orderId, userId);
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        projectAssignmentOntoOrder(order, assignment);
        var accidentVehicles = accidentVehicleMapper.findByOrderId(orderId);
        order.setAccidentVehicles(accidentVehicles == null ? List.of() : accidentVehicles);
        RescuerTaskDetail detail = new RescuerTaskDetail();
        detail.setOrder(order);
        DispatchFieldRecord fr = fieldRecordMapper.findByAssignmentId(assignment.getId());
        if (fr == null) {
            fr = fieldRecordMapper.findByOrderId(orderId);
        }
        detail.setFieldRecord(fr);
        detail.setEvaluation(evaluationMapper.findByOrderId(orderId));
        RescueVehicle vehicle = rescueVehicleMapper.findById(assignment.getVehicleId());
        if (vehicle != null) {
            AssignedVehicleSnapshot snap = new AssignedVehicleSnapshot();
            snap.setId(vehicle.getId());
            snap.setPlateNo(vehicle.getPlateNo());
            snap.setLongitude(vehicle.getLongitude());
            snap.setLatitude(vehicle.getLatitude());
            snap.setLocationUpdatedAt(vehicle.getLocationUpdatedAt());
            detail.setAssignedVehicle(snap);
        }
        return detail;
    }

    @Transactional
    public void saveScene(Long userId, Long orderId, SceneRequest req) {
        DispatchVehicleAssignment assignment = requireAssignmentAccepted(orderId, userId);
        DispatchFieldRecord record = getOrCreateRecord(orderId, assignment.getId());
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
        DispatchVehicleAssignment assignment = requireAssignmentAccepted(orderId, userId);
        DispatchFieldRecord record = getOrCreateRecord(orderId, assignment.getId());
        if (req != null) {
            record.setParkAddress(req.getParkAddress());
            record.setParkRemark(req.getParkRemark());
        }
        record.setParkSubmittedAt(LocalDateTime.now());
        upsert(record);
    }

    @Transactional
    public DetainedVehicle detainIn(Long userId, Long orderId, DetainInRequest req) {
        if (req == null) {
            throw new RuntimeException("入库参数无效");
        }
        DispatchVehicleAssignment assignment = requireAssignmentAccepted(orderId, userId);
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        String plate = req.getPlateNo() == null ? "" : req.getPlateNo().trim();
        if (plate.isEmpty()) {
            throw new RuntimeException("车牌不能为空");
        }
        if (!matchesAccidentVehicle(orderId, plate)) {
            throw new RuntimeException("车牌与工单事故车不一致");
        }
        User user = userMapper.findById(userId);
        String rescuerName = null;
        if (user != null) {
            rescuerName = user.getRealName() != null && !user.getRealName().isBlank()
                    ? user.getRealName() : user.getUsername();
        }
        req.setDispatchOrderId(orderId);
        req.setRescuerName(rescuerName);

        DetainedVehicle created = detainedVehicleService.checkIn(req, userId);

        DispatchFieldRecord record = getOrCreateRecord(orderId, assignment.getId());
        record.setParkAddress(buildParkAddress(req));
        record.setParkRemark(req.getRemark());
        record.setParkSubmittedAt(LocalDateTime.now());
        upsert(record);
        return created;
    }

    public DetainedVehicle getTaskDetain(Long userId, Long orderId) {
        requireAssignment(orderId, userId);
        return detainedVehicleMapper.findInYardByDispatchOrderId(orderId);
    }

    public String previewEntryNo(Long userId, Long orderId) {
        requireAssignment(orderId, userId);
        return entryNoGenerator.generate(orderId);
    }

    public List<ParkingLot> listEnabledParkingLots() {
        PageParams pp = PageParams.normalize(1, 100);
        Map<String, Object> result = parkingLotService.list(null, "ENABLED", pp);
        Object rows = result == null ? null : result.get("list");
        if (rows instanceof List<?> list) {
            List<ParkingLot> lots = new ArrayList<>();
            for (Object row : list) {
                if (row instanceof ParkingLot lot) {
                    lots.add(lot);
                }
            }
            return lots;
        }
        return List.of();
    }

    public List<ParkingArea> listParkingAreas(Long lotId) {
        return parkingAreaService.list(lotId, "ENABLED");
    }

    @Transactional
    public DetainMedia addDetainMedia(Long userId, Long detainId, String bizType, MultipartFile file) {
        DetainedVehicle dv = detainedVehicleMapper.findById(detainId);
        if (dv == null) {
            throw new RuntimeException("扣留车辆不存在");
        }
        if (dv.getDispatchOrderId() == null) {
            throw new RuntimeException("扣留车辆未关联工单");
        }
        requireAssignmentAccepted(dv.getDispatchOrderId(), userId);
        if (!"PARK".equals(bizType)) {
            throw new RuntimeException("媒体类型无效，仅支持 PARK");
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
        media.setBizType("PARK");
        media.setFilePath(relativePath);
        media.setSortOrder(0);
        media.setUploadedBy(userId);
        detainMediaMapper.insert(media);
        return media;
    }

    private boolean matchesAccidentVehicle(Long orderId, String plate) {
        List<DispatchAccidentVehicle> vehicles = accidentVehicleMapper.findByOrderId(orderId);
        if (vehicles == null || vehicles.isEmpty()) {
            return false;
        }
        String needle = PlateNos.normalize(plate);
        for (DispatchAccidentVehicle av : vehicles) {
            if (av != null && needle.equals(PlateNos.normalize(av.getPlateNo()))) {
                return true;
            }
        }
        return false;
    }

    private String buildParkAddress(DetainInRequest req) {
        ParkingLot lot = parkingLotService.findById(req.getParkingLotId());
        StringBuilder sb = new StringBuilder(lot != null && lot.getName() != null ? lot.getName() : "停车场");
        if (req.getParkingAreaId() != null) {
            ParkingArea area = parkingAreaService.findById(req.getParkingAreaId());
            if (area != null && area.getName() != null) {
                sb.append('/').append(area.getName());
            }
        }
        if (req.getStallNo() != null && !req.getStallNo().isBlank()) {
            sb.append(" 车位").append(req.getStallNo().trim());
        }
        return sb.toString();
    }

    @Transactional
    public DispatchMedia addMedia(Long userId, Long orderId, String bizType, MultipartFile file) {
        DispatchVehicleAssignment assignment = requireAssignmentAccepted(orderId, userId);
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
        media.setAssignmentId(assignment.getId());
        media.setBizType(bizType);
        media.setFilePath(relativePath);
        media.setSortOrder(0);
        media.setUploadedBy(userId);
        mediaMapper.insert(media);
        return media;
    }

    @Transactional
    public void deleteMedia(Long userId, Long orderId, Long mediaId) {
        DispatchMedia media = getOwnedMedia(userId, orderId, mediaId);
        if (media.getFilePath() != null) {
            fileStorageService.deleteDispatchMedia(media.getFilePath());
        }
        mediaMapper.deleteById(mediaId);
    }

    public DispatchMedia getOwnedMedia(Long userId, Long orderId, Long mediaId) {
        DispatchVehicleAssignment assignment = requireAssignmentAccepted(orderId, userId);
        DispatchMedia media = mediaMapper.findById(mediaId);
        if (media == null || !orderId.equals(media.getDispatchOrderId())) {
            throw new RuntimeException("照片不存在");
        }
        if (media.getAssignmentId() != null && !assignment.getId().equals(media.getAssignmentId())) {
            throw new RuntimeException("照片不存在");
        }
        return media;
    }

    public List<DispatchMedia> listMedia(Long userId, Long orderId) {
        DispatchVehicleAssignment assignment = requireAssignment(orderId, userId);
        List<DispatchMedia> byAssignment = mediaMapper.findByAssignmentId(assignment.getId());
        if (byAssignment != null && !byAssignment.isEmpty()) {
            return byAssignment;
        }
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

    private void projectAssignmentOntoOrder(DispatchOrder order, DispatchVehicleAssignment a) {
        order.setVehicleId(a.getVehicleId());
        order.setRescuerId(a.getRescuerId());
        order.setStatus(a.getStatus());
        order.setAcceptedAt(a.getAcceptedAt());
        order.setCheckedInAt(a.getCheckedInAt());
        order.setCheckinLng(a.getCheckinLng());
        order.setCheckinLat(a.getCheckinLat());
        order.setCheckinMode(a.getCheckinMode());
        order.setCheckinRemark(a.getCheckinRemark());
        order.setRejectReason(a.getRejectReason());
        RescueVehicle v = rescueVehicleMapper.findById(a.getVehicleId());
        if (v != null) {
            order.setVehiclePlate(v.getPlateNo());
        }
    }

    private DispatchVehicleAssignment requireAssignment(Long orderId, Long userId) {
        if (dispatchOrderMapper.findById(orderId) == null) {
            throw new RuntimeException("工单不存在");
        }
        if (userId == null) {
            throw new RuntimeException("无权操作该工单");
        }
        DispatchVehicleAssignment a = assignmentMapper.findByOrderIdAndRescuerId(orderId, userId);
        if (a == null) {
            throw new RuntimeException("无权操作该工单");
        }
        return a;
    }

    private DispatchVehicleAssignment requireAssignmentAccepted(Long orderId, Long userId) {
        DispatchVehicleAssignment a = requireAssignment(orderId, userId);
        if (!"ACCEPTED".equals(a.getStatus())) {
            throw new RuntimeException("仅已接单状态可操作");
        }
        return a;
    }

    private DispatchFieldRecord getOrCreateRecord(Long orderId, Long assignmentId) {
        DispatchFieldRecord existing = fieldRecordMapper.findByAssignmentId(assignmentId);
        if (existing != null) {
            return existing;
        }
        DispatchFieldRecord created = new DispatchFieldRecord();
        created.setDispatchOrderId(orderId);
        created.setAssignmentId(assignmentId);
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
