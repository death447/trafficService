package com.example.backend.service;

import com.example.backend.entity.AccidentVehicleType;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import com.example.backend.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DispatchOrderService {

    @Autowired
    private DispatchOrderMapper dispatchOrderMapper;

    @Autowired
    private RescueVehicleService rescueVehicleService;

    @Autowired
    private RescueVehicleMapper rescueVehicleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AccidentVehicleTypeService accidentVehicleTypeService;

    public DispatchOrder findById(Long id) {
        DispatchOrder order = dispatchOrderMapper.findById(id);
        if (order != null) {
            enrich(order);
        }
        return order;
    }

    public List<DispatchOrder> findAll() {
        return dispatchOrderMapper.findAll();
    }

    public List<DispatchOrder> list(String orderNo, String status, String address, Long dispatcherId) {
        return dispatchOrderMapper.findAll().stream()
                .filter(o -> {
                    if (orderNo != null && !orderNo.isEmpty()) {
                        String no = o.getOrderNo() != null ? o.getOrderNo() : "";
                        if (!no.contains(orderNo)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isEmpty()) {
                        if (!status.equals(o.getStatus())) {
                            return false;
                        }
                    }
                    if (address != null && !address.isEmpty()) {
                        String addr = o.getAccidentAddress() != null ? o.getAccidentAddress() : "";
                        if (!addr.contains(address)) {
                            return false;
                        }
                    }
                    if (dispatcherId != null) {
                        if (!dispatcherId.equals(o.getDispatcherId())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean create(DispatchOrder order, Long currentUserId) {
        order.setOrderNo(generateOrderNo());
        order.setStatus("PENDING");
        applyDispatcherAndPrefill(order, currentUserId);
        applyPlateAndVehicleType(order);
        order.setAbortReason(null);
        order.setDispatchedAt(null);
        order.setCompletedAt(null);
        return dispatchOrderMapper.insert(order) > 0;
    }

    @Transactional
    public boolean update(DispatchOrder order) {
        DispatchOrder existing = dispatchOrderMapper.findById(order.getId());
        if (existing == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"PENDING".equals(existing.getStatus())) {
            throw new RuntimeException("仅待派单状态可编辑");
        }
        existing.setAccidentAddress(order.getAccidentAddress());
        existing.setLongitude(order.getLongitude());
        existing.setLatitude(order.getLatitude());
        existing.setRescueReason(order.getRescueReason());
        Long previousTypeId = existing.getVehicleTypeId();
        String previousTypeName = existing.getVehicleTypeName();
        existing.setPlateNo(order.getPlateNo());
        existing.setVehicleTypeId(order.getVehicleTypeId());
        applyPlateAndVehicleType(existing, previousTypeId, previousTypeName);
        Long previousDispatcherId = existing.getDispatcherId();
        existing.setDispatcherId(order.getDispatcherId());
        if (order.getRescuerId() != null) {
            existing.setRescuerId(order.getRescuerId());
        }
        if (order.getVehicleId() != null) {
            existing.setVehicleId(order.getVehicleId());
        }
        applyDispatcherAndPrefill(existing, previousDispatcherId);
        return dispatchOrderMapper.update(existing) > 0;
    }

    void applyPlateAndVehicleType(DispatchOrder order) {
        applyPlateAndVehicleType(order, null, null);
    }

    void applyPlateAndVehicleType(DispatchOrder order, Long previousTypeId, String previousTypeName) {
        String plate = order.getPlateNo();
        if (plate != null) {
            plate = plate.trim();
            order.setPlateNo(plate.isEmpty() ? null : plate);
        }
        Long typeId = order.getVehicleTypeId();
        if (typeId == null) {
            order.setVehicleTypeId(null);
            order.setVehicleTypeName(null);
            return;
        }
        if (previousTypeId != null && previousTypeId.equals(typeId) && previousTypeName != null) {
            order.setVehicleTypeId(previousTypeId);
            order.setVehicleTypeName(previousTypeName);
            return;
        }
        AccidentVehicleType type = accidentVehicleTypeService.requireEnabled(typeId);
        order.setVehicleTypeId(type.getId());
        order.setVehicleTypeName(type.getName());
    }

    void applyDispatcherAndPrefill(DispatchOrder order, Long currentUserId) {
        Long dispatcherId = order.getDispatcherId() != null ? order.getDispatcherId() : currentUserId;
        assertHasRole(dispatcherId, "DISPATCHER", "ADMIN");
        order.setDispatcherId(dispatcherId);

        if (order.getVehicleId() != null) {
            RescueVehicle vehicle = rescueVehicleService.requireIdle(order.getVehicleId());
            // 施救员由车辆绑定关系带出，不允许单独指定
            order.setRescuerId(vehicle.getDriverUserId());
        }
        if (order.getRescuerId() != null) {
            assertHasRole(order.getRescuerId(), "TOW_DRIVER");
        }
    }

    void assertHasRole(Long userId, String... roleCodes) {
        if (userId == null) {
            throw new RuntimeException("用户无效");
        }
        List<Role> roles = userMapper.findRolesByUserId(userId);
        Set<String> codes = roles == null
                ? Set.of()
                : roles.stream().map(Role::getRoleCode).collect(Collectors.toSet());
        boolean ok = Arrays.stream(roleCodes).anyMatch(codes::contains);
        if (!ok) {
            if (Arrays.asList(roleCodes).contains("TOW_DRIVER")) {
                throw new RuntimeException("施救员角色无效");
            }
            throw new RuntimeException("调度员角色无效");
        }
    }

    @Transactional
    public void assign(Long orderId, Long vehicleId, Long rescuerId) {
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("仅待派单状态可派车");
        }
        rescueVehicleService.requireIdle(vehicleId);
        order.setVehicleId(vehicleId);
        order.setRescuerId(rescuerId);
        order.setStatus("DISPATCHED");
        order.setDispatchedAt(LocalDateTime.now());
        dispatchOrderMapper.update(order);
        rescueVehicleService.markBusy(vehicleId);
    }

    @Transactional
    public void accept(Long orderId, Long rescuerId) {
        DispatchOrder order = requireOrder(orderId);
        assertRescuer(order, rescuerId);
        if (!"DISPATCHED".equals(order.getStatus())) {
            throw new RuntimeException("仅已派单状态可接单");
        }
        order.setStatus("ACCEPTED");
        order.setAcceptedAt(LocalDateTime.now());
        dispatchOrderMapper.update(order);
    }

    @Transactional
    public void reject(Long orderId, Long rescuerId, String reason) {
        DispatchOrder order = requireOrder(orderId);
        assertRescuer(order, rescuerId);
        if (!"DISPATCHED".equals(order.getStatus()) && !"ACCEPTED".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不可退单");
        }
        if (order.getCheckedInAt() != null) {
            throw new RuntimeException("已签到不可退单");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("请填写退单原因");
        }
        Long vehicleId = order.getVehicleId();
        order.setStatus("PENDING");
        order.setRejectReason(reason.trim());
        order.setVehicleId(null);
        order.setRescuerId(null);
        order.setDispatchedAt(null);
        order.setAcceptedAt(null);
        dispatchOrderMapper.update(order);
        releaseVehicleIfUnused(vehicleId);
    }

    @Transactional
    public void checkin(Long orderId, Long rescuerId, BigDecimal lng, BigDecimal lat, String mode, String remark) {
        DispatchOrder order = requireOrder(orderId);
        assertRescuer(order, rescuerId);
        if (!"ACCEPTED".equals(order.getStatus())) {
            throw new RuntimeException("仅已接单状态可签到");
        }
        if (order.getCheckedInAt() != null) {
            throw new RuntimeException("已签到");
        }
        if ("AUTO".equals(mode)) {
            if (order.getLongitude() == null || order.getLatitude() == null) {
                throw new RuntimeException("工单缺少事故坐标，请使用手动签到");
            }
            if (lng == null || lat == null) {
                throw new RuntimeException("自动签到需要定位坐标");
            }
            double meters = GeoUtils.distanceMeters(order.getLongitude(), order.getLatitude(), lng, lat);
            if (meters > 500.0) {
                throw new RuntimeException("距离事故点超过500米，无法自动签到");
            }
            order.setCheckinLng(lng);
            order.setCheckinLat(lat);
        } else if ("MANUAL".equals(mode)) {
            if (remark == null || remark.isBlank()) {
                throw new RuntimeException("手动签到须填写原因");
            }
            order.setCheckinRemark(remark.trim());
            order.setCheckinLng(lng);
            order.setCheckinLat(lat);
        } else {
            throw new RuntimeException("签到模式无效");
        }
        order.setCheckinMode(mode);
        order.setCheckedInAt(LocalDateTime.now());
        dispatchOrderMapper.update(order);
    }

    // complete: 允许 DISPATCHED（兼容旧 PC）或 ACCEPTED；若 ACCEPTED 则必须已签到
    @Transactional
    public void complete(Long orderId) {
        DispatchOrder order = requireOrder(orderId);
        if ("ACCEPTED".equals(order.getStatus())) {
            if (order.getCheckedInAt() == null) {
                throw new RuntimeException("请先签到再完成");
            }
        } else if (!"DISPATCHED".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不可完成");
        }
        Long vehicleId = order.getVehicleId();
        order.setStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        dispatchOrderMapper.update(order);
        releaseVehicleIfUnused(vehicleId);
    }

    @Transactional
    public void abort(Long orderId, String abortReason) {
        DispatchOrder order = requireOrder(orderId);
        String st = order.getStatus();
        if (!"PENDING".equals(st) && !"DISPATCHED".equals(st) && !"ACCEPTED".equals(st)) {
            throw new RuntimeException("当前状态不可作废");
        }
        Long vehicleId = order.getVehicleId();
        boolean occupied = "DISPATCHED".equals(st) || "ACCEPTED".equals(st);
        order.setStatus("ABORTED");
        order.setAbortReason(abortReason);
        // 保留 rescuer_id / vehicle_id 历史信息供列表；释放占用
        dispatchOrderMapper.update(order);
        if (occupied) {
            releaseVehicleIfUnused(vehicleId);
        }
    }

    private DispatchOrder requireOrder(Long id) {
        DispatchOrder order = dispatchOrderMapper.findById(id);
        if (order == null) throw new RuntimeException("工单不存在");
        return order;
    }

    private void assertRescuer(DispatchOrder order, Long rescuerId) {
        if (rescuerId == null || !rescuerId.equals(order.getRescuerId())) {
            throw new RuntimeException("无权操作该工单");
        }
    }

    String generateOrderNo() {
        String prefix = "RO" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = dispatchOrderMapper.countByOrderNoPrefix(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    void releaseVehicleIfUnused(Long vehicleId) {
        if (vehicleId != null && dispatchOrderMapper.countDispatchedByVehicleId(vehicleId) == 0) {
            rescueVehicleService.markIdle(vehicleId);
        }
    }

    private void enrich(DispatchOrder order) {
        if (order.getVehicleId() != null) {
            RescueVehicle vehicle = rescueVehicleMapper.findById(order.getVehicleId());
            if (vehicle != null) {
                order.setVehiclePlate(vehicle.getPlateNo());
            }
        }
        if (order.getDispatcherId() != null) {
            User user = userMapper.findById(order.getDispatcherId());
            if (user != null) {
                order.setDispatcherName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            }
        }
        if (order.getRescuerId() != null) {
            User user = userMapper.findById(order.getRescuerId());
            if (user != null) {
                order.setRescuerName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            }
        }
    }
}
