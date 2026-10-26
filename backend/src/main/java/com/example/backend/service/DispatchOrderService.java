package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.DispatchOverview;
import com.example.backend.entity.AccidentVehicleType;
import com.example.backend.entity.DispatchAccidentVehicle;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchVehicleAssignment;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.mapper.DispatchAccidentVehicleMapper;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.DispatchVehicleAssignmentMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import com.example.backend.util.GeoUtils;
import com.example.backend.util.PlateNos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private DispatchFieldRecordMapper fieldRecordMapper;

    @Autowired
    private DispatchMediaMapper mediaMapper;

    @Autowired
    private DispatchOrderEvaluationMapper evaluationMapper;

    @Autowired
    private DispatchVehicleAssignmentMapper assignmentMapper;

    @Autowired
    private DispatchAccidentVehicleMapper accidentVehicleMapper;

    public DispatchOrder findById(Long id) {
        DispatchOrder order = dispatchOrderMapper.findById(id);
        if (order != null) {
            enrich(order);
            order.setFieldRecord(fieldRecordMapper.findByOrderId(id));
            order.setMedias(mediaMapper.findByOrderId(id));
            order.setEvaluation(evaluationMapper.findByOrderId(id));
        }
        return order;
    }

    public List<DispatchOrder> findAll() {
        return dispatchOrderMapper.findAll();
    }

    public Map<String, Object> list(String orderNo, String status, String address,
                                    Long dispatcherId, PageParams pp) {
        long total = dispatchOrderMapper.count(orderNo, status, address, dispatcherId);
        List<DispatchOrder> orders = dispatchOrderMapper.selectPage(
                orderNo, status, address, dispatcherId, pp.getOffset(), pp.getSize());
        orders.forEach(this::enrich);
        return pp.toResult(orders, total);
    }

    public DispatchOverview overview() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        DispatchOverview overview = new DispatchOverview();
        overview.setTodayTotal(dispatchOrderMapper.countCreatedBetween(start, end));
        overview.setTodayDispatched(dispatchOrderMapper.countDispatchedBetween(start, end));
        overview.setTodayCompleted(dispatchOrderMapper.countCompletedBetween(start, end));
        overview.setInProgress(dispatchOrderMapper.countActive());
        List<DispatchOrder> accidents = dispatchOrderMapper.findActiveWithCoords();
        overview.setAccidents(accidents == null ? List.of() : accidents);
        return overview;
    }

    @Transactional
    public boolean create(DispatchOrder order, Long currentUserId) {
        List<Long> toAssign = resolveVehicleIdsToAssign(order);
        order.setOrderNo(generateOrderNo());
        order.setStatus("PENDING");
        applyDispatcherAndPrefill(order, currentUserId);
        List<DispatchAccidentVehicle> vehicles = normalizeAndValidateAccidentVehicles(
                resolveAccidentVehiclesInput(order));
        syncFirstAccidentVehicleToOrder(order, vehicles.get(0));
        order.setAbortReason(null);
        order.setDispatchedAt(null);
        order.setCompletedAt(null);
        if (dispatchOrderMapper.insert(order) <= 0) {
            return false;
        }
        replaceAccidentVehicles(order.getId(), vehicles);
        order.setAccidentVehicles(vehicles);
        if (!toAssign.isEmpty()) {
            assign(order.getId(), toAssign);
            DispatchOrder latest = dispatchOrderMapper.findById(order.getId());
            if (latest != null) {
                order.setStatus(latest.getStatus());
                order.setVehicleId(latest.getVehicleId());
                order.setRescuerId(latest.getRescuerId());
                order.setDispatchedAt(latest.getDispatchedAt());
            }
        }
        return true;
    }

    /** Merge vehicleId + vehicleIds, clear prefill columns so insert stays clean. */
    List<Long> resolveVehicleIdsToAssign(DispatchOrder order) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (order.getVehicleIds() != null) {
            for (Long id : order.getVehicleIds()) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (order.getVehicleId() != null) {
            ids.add(order.getVehicleId());
        }
        order.setVehicleId(null);
        order.setRescuerId(null);
        order.setVehicleIds(null);
        return new ArrayList<>(ids);
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
        List<DispatchAccidentVehicle> vehicles = normalizeAndValidateAccidentVehicles(
                resolveAccidentVehiclesInput(order));
        replaceAccidentVehicles(existing.getId(), vehicles);
        syncFirstAccidentVehicleToOrder(existing, vehicles.get(0));
        existing.setAccidentVehicles(vehicles);
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

    List<DispatchAccidentVehicle> resolveAccidentVehiclesInput(DispatchOrder order) {
        List<DispatchAccidentVehicle> list = order.getAccidentVehicles();
        if (list != null && !list.isEmpty()) {
            return list;
        }
        String plate = order.getPlateNo();
        if (plate != null && !plate.trim().isEmpty()) {
            DispatchAccidentVehicle one = new DispatchAccidentVehicle();
            one.setPlateNo(plate);
            one.setVehicleTypeId(order.getVehicleTypeId());
            one.setVehicleTypeName(order.getVehicleTypeName());
            one.setPartyName(order.getPartyName());
            one.setPartyPhone(order.getPartyPhone());
            return List.of(one);
        }
        return list == null ? List.of() : list;
    }

    List<DispatchAccidentVehicle> normalizeAndValidateAccidentVehicles(List<DispatchAccidentVehicle> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new RuntimeException("请至少添加一辆事故车");
        }
        List<DispatchAccidentVehicle> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DispatchAccidentVehicle in : raw) {
            if (in == null) {
                throw new RuntimeException("事故车牌不能为空");
            }
            String plate = in.getPlateNo() == null ? "" : in.getPlateNo().trim();
            if (plate.isEmpty()) {
                throw new RuntimeException("事故车牌不能为空");
            }
            String key = PlateNos.normalize(plate);
            if (!seen.add(key)) {
                throw new RuntimeException("事故车牌重复");
            }
            DispatchAccidentVehicle row = new DispatchAccidentVehicle();
            row.setPlateNo(plate);
            String partyName = in.getPartyName();
            if (partyName != null) {
                partyName = partyName.trim();
                row.setPartyName(partyName.isEmpty() ? null : partyName);
            }
            String partyPhone = in.getPartyPhone();
            if (partyPhone != null) {
                partyPhone = partyPhone.trim();
                row.setPartyPhone(partyPhone.isEmpty() ? null : partyPhone);
            }
            Long typeId = in.getVehicleTypeId();
            if (typeId == null) {
                row.setVehicleTypeId(null);
                row.setVehicleTypeName(null);
            } else {
                AccidentVehicleType type = accidentVehicleTypeService.requireEnabled(typeId);
                row.setVehicleTypeId(type.getId());
                row.setVehicleTypeName(type.getName());
            }
            result.add(row);
        }
        return result;
    }

    void replaceAccidentVehicles(Long orderId, List<DispatchAccidentVehicle> vehicles) {
        accidentVehicleMapper.deleteByOrderId(orderId);
        for (int i = 0; i < vehicles.size(); i++) {
            DispatchAccidentVehicle v = vehicles.get(i);
            v.setDispatchOrderId(orderId);
            v.setSortOrder(i);
            accidentVehicleMapper.insert(v);
        }
    }

    void syncFirstAccidentVehicleToOrder(DispatchOrder order, DispatchAccidentVehicle first) {
        order.setPlateNo(first.getPlateNo());
        order.setVehicleTypeId(first.getVehicleTypeId());
        order.setVehicleTypeName(first.getVehicleTypeName());
        order.setPartyName(first.getPartyName());
        order.setPartyPhone(first.getPartyPhone());
    }

    void normalizePartyContact(DispatchOrder order) {
        if (order.getPartyName() != null) {
            String n = order.getPartyName().trim();
            order.setPartyName(n.isEmpty() ? null : n);
        }
        if (order.getPartyPhone() != null) {
            String p = order.getPartyPhone().trim();
            order.setPartyPhone(p.isEmpty() ? null : p);
        }
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
        // create 时车辆改由 resolveVehicleIdsToAssign + assign；update 仍可带 vehicleId 镜像
        if (order.getVehicleId() != null) {
            RescueVehicle vehicle = rescueVehicleService.requireAssignable(order.getVehicleId());
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
    public void assign(Long orderId, List<Long> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            throw new RuntimeException("请选择施救车辆");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : vehicleIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            throw new RuntimeException("请选择施救车辆");
        }
        for (Long vehicleId : unique) {
            assignOne(orderId, vehicleId);
        }
    }

    @Transactional
    public void assign(Long orderId, Long vehicleId, Long rescuerIdIgnored) {
        assign(orderId, List.of(vehicleId));
    }

    private void assignOne(Long orderId, Long vehicleId) {
        DispatchOrder order = requireOrder(orderId);
        String st = order.getStatus();
        if ("COMPLETED".equals(st) || "ABORTED".equals(st)) {
            throw new RuntimeException("当前状态不可派车");
        }
        if (assignmentMapper.countByOrderIdAndVehicleId(orderId, vehicleId) > 0) {
            throw new RuntimeException("该车辆已派到本工单");
        }
        RescueVehicle vehicle = rescueVehicleService.requireAssignable(vehicleId);
        Long driverId = vehicle.getDriverUserId();
        assertHasRole(driverId, "TOW_DRIVER");

        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setDispatchOrderId(orderId);
        a.setVehicleId(vehicleId);
        a.setRescuerId(driverId);
        a.setStatus("DISPATCHED");
        a.setDispatchedAt(LocalDateTime.now());
        assignmentMapper.insert(a);

        if (order.getDispatchedAt() == null) {
            order.setDispatchedAt(LocalDateTime.now());
        }
        order.setRejectReason(null);
        // 兼容列：指向最近一次派车
        order.setVehicleId(vehicleId);
        order.setRescuerId(driverId);
        dispatchOrderMapper.update(order);
        rescueVehicleService.markBusy(vehicleId);
        refreshOrderStatus(orderId);
    }

    @Transactional
    public void accept(Long orderId, Long rescuerId) {
        requireOrder(orderId);
        DispatchVehicleAssignment a = requireAssignmentForRescuer(orderId, rescuerId);
        if (!"DISPATCHED".equals(a.getStatus())) {
            throw new RuntimeException("仅已派单状态可接单");
        }
        a.setStatus("ACCEPTED");
        a.setAcceptedAt(LocalDateTime.now());
        assignmentMapper.update(a);
        refreshOrderStatus(orderId);
    }

    @Transactional
    public void reject(Long orderId, Long rescuerId, String reason) {
        requireOrder(orderId);
        DispatchVehicleAssignment a = requireAssignmentForRescuer(orderId, rescuerId);
        if (!"DISPATCHED".equals(a.getStatus()) && !"ACCEPTED".equals(a.getStatus())) {
            throw new RuntimeException("当前状态不可退单");
        }
        if (a.getCheckedInAt() != null) {
            throw new RuntimeException("已签到不可退单");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("请填写退单原因");
        }
        Long vehicleId = a.getVehicleId();
        a.setStatus("REJECTED");
        a.setRejectReason(reason.trim());
        assignmentMapper.update(a);
        refreshOrderStatus(orderId);
        releaseVehicleIfUnused(vehicleId);
    }

    @Transactional
    public void checkin(Long orderId, Long rescuerId, BigDecimal lng, BigDecimal lat, String mode, String remark) {
        DispatchOrder order = requireOrder(orderId);
        DispatchVehicleAssignment a = requireAssignmentForRescuer(orderId, rescuerId);
        if (!"ACCEPTED".equals(a.getStatus())) {
            throw new RuntimeException("仅已接单状态可签到");
        }
        if (a.getCheckedInAt() != null) {
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
            a.setCheckinLng(lng);
            a.setCheckinLat(lat);
        } else if ("MANUAL".equals(mode)) {
            if (remark == null || remark.isBlank()) {
                throw new RuntimeException("手动签到须填写原因");
            }
            a.setCheckinRemark(remark.trim());
            a.setCheckinLng(lng);
            a.setCheckinLat(lat);
        } else {
            throw new RuntimeException("签到模式无效");
        }
        a.setCheckinMode(mode);
        a.setCheckedInAt(LocalDateTime.now());
        assignmentMapper.update(a);
    }

    /** PC：有活跃赋值则拒绝；否则若汇总已可完成则幂等。 */
    @Transactional
    public void complete(Long orderId) {
        requireOrder(orderId);
        List<DispatchVehicleAssignment> list = assignmentMapper.findByOrderId(orderId);
        boolean anyActive = list.stream()
                .anyMatch(a -> "DISPATCHED".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus()));
        if (anyActive) {
            throw new RuntimeException("仍有未完成的施救车辆，请等待各车完成或中止后再操作");
        }
        boolean anyCompleted = list.stream().anyMatch(a -> "COMPLETED".equals(a.getStatus()));
        if (!anyCompleted) {
            throw new RuntimeException("当前状态不可完成");
        }
        refreshOrderStatus(orderId);
    }

    /** 移动端：完成本人赋值。 */
    @Transactional
    public void complete(Long orderId, Long rescuerId) {
        requireOrder(orderId);
        DispatchVehicleAssignment a = requireAssignmentForRescuer(orderId, rescuerId);
        if ("ACCEPTED".equals(a.getStatus())) {
            if (a.getCheckedInAt() == null) {
                throw new RuntimeException("请先签到再完成");
            }
        } else if (!"DISPATCHED".equals(a.getStatus())) {
            throw new RuntimeException("当前状态不可完成");
        }
        Long vehicleId = a.getVehicleId();
        a.setStatus("COMPLETED");
        a.setCompletedAt(LocalDateTime.now());
        assignmentMapper.update(a);
        refreshOrderStatus(orderId);
        releaseVehicleIfUnused(vehicleId);
    }

    @Transactional
    public void abort(Long orderId, String abortReason) {
        DispatchOrder order = requireOrder(orderId);
        String st = order.getStatus();
        if (!"PENDING".equals(st) && !"DISPATCHED".equals(st) && !"ACCEPTED".equals(st)) {
            throw new RuntimeException("当前状态不可作废");
        }
        List<DispatchVehicleAssignment> list = assignmentMapper.findByOrderId(orderId);
        Set<Long> vehicleIds = new LinkedHashSet<>();
        for (DispatchVehicleAssignment a : list) {
            if ("DISPATCHED".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus())) {
                a.setStatus("ABORTED");
                assignmentMapper.update(a);
                vehicleIds.add(a.getVehicleId());
            }
        }
        order.setStatus("ABORTED");
        order.setAbortReason(abortReason);
        dispatchOrderMapper.update(order);
        for (Long vehicleId : vehicleIds) {
            releaseVehicleIfUnused(vehicleId);
        }
    }

    void refreshOrderStatus(Long orderId) {
        DispatchOrder order = requireOrder(orderId);
        List<DispatchVehicleAssignment> list = assignmentMapper.findByOrderId(orderId);
        if (list == null || list.isEmpty()) {
            order.setStatus("PENDING");
            order.setVehicleId(null);
            order.setRescuerId(null);
            order.setCompletedAt(null);
            dispatchOrderMapper.update(order);
            return;
        }
        boolean anyDispatched = false;
        boolean anyAccepted = false;
        boolean anyCompleted = false;
        boolean allTerminal = true;
        for (DispatchVehicleAssignment a : list) {
            String s = a.getStatus();
            if ("DISPATCHED".equals(s)) {
                anyDispatched = true;
                allTerminal = false;
            } else if ("ACCEPTED".equals(s)) {
                anyAccepted = true;
                allTerminal = false;
            } else if ("COMPLETED".equals(s)) {
                anyCompleted = true;
            } else if (!"REJECTED".equals(s) && !"ABORTED".equals(s)) {
                allTerminal = false;
            }
        }
        if (!allTerminal) {
            if (anyDispatched) {
                order.setStatus("DISPATCHED");
            } else if (anyAccepted) {
                order.setStatus("ACCEPTED");
            }
            order.setCompletedAt(null);
        } else if (anyCompleted) {
            order.setStatus("COMPLETED");
            if (order.getCompletedAt() == null) {
                order.setCompletedAt(LocalDateTime.now());
            }
        } else {
            // 全部 REJECTED/ABORTED 且无完成 → 回 PENDING 可再派
            order.setStatus("PENDING");
            order.setCompletedAt(null);
            order.setVehicleId(null);
            order.setRescuerId(null);
        }
        // 兼容列：活跃赋值优先，否则第一行
        DispatchVehicleAssignment primary = list.stream()
                .filter(a -> "DISPATCHED".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus()))
                .findFirst()
                .orElse(list.get(0));
        if (!"PENDING".equals(order.getStatus())) {
            order.setVehicleId(primary.getVehicleId());
            order.setRescuerId(primary.getRescuerId());
        }
        dispatchOrderMapper.update(order);
    }

    private DispatchOrder requireOrder(Long id) {
        DispatchOrder order = dispatchOrderMapper.findById(id);
        if (order == null) throw new RuntimeException("工单不存在");
        return order;
    }

    private DispatchVehicleAssignment requireAssignmentForRescuer(Long orderId, Long rescuerId) {
        if (rescuerId == null) {
            throw new RuntimeException("无权操作该工单");
        }
        DispatchVehicleAssignment a = assignmentMapper.findByOrderIdAndRescuerId(orderId, rescuerId);
        if (a == null) {
            throw new RuntimeException("无权操作该工单");
        }
        return a;
    }

    String generateOrderNo() {
        String prefix = "RO" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = dispatchOrderMapper.countByOrderNoPrefix(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    void releaseVehicleIfUnused(Long vehicleId) {
        if (vehicleId != null && assignmentMapper.countActiveByVehicleId(vehicleId) == 0) {
            rescueVehicleService.markIdle(vehicleId);
        }
    }

    private void enrich(DispatchOrder order) {
        List<DispatchAccidentVehicle> accidentVehicles = accidentVehicleMapper.findByOrderId(order.getId());
        order.setAccidentVehicles(accidentVehicles == null ? List.of() : accidentVehicles);

        List<DispatchVehicleAssignment> assignments = assignmentMapper.findByOrderId(order.getId());
        if (assignments == null) {
            assignments = List.of();
        }
        for (DispatchVehicleAssignment a : assignments) {
            RescueVehicle vehicle = rescueVehicleMapper.findById(a.getVehicleId());
            if (vehicle != null) {
                a.setVehiclePlate(vehicle.getPlateNo());
            }
            if (a.getRescuerId() != null) {
                User user = userMapper.findById(a.getRescuerId());
                if (user != null) {
                    a.setRescuerName(user.getRealName() != null ? user.getRealName() : user.getUsername());
                }
            }
        }
        order.setAssignments(assignments);

        Long displayVehicleId = order.getVehicleId();
        Long displayRescuerId = order.getRescuerId();
        if (!assignments.isEmpty()) {
            DispatchVehicleAssignment primary = assignments.stream()
                    .filter(a -> "DISPATCHED".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus()))
                    .findFirst()
                    .orElse(assignments.get(0));
            displayVehicleId = primary.getVehicleId();
            displayRescuerId = primary.getRescuerId();
            // 将当前主赋值过程字段投影到工单，兼容旧移动端读法（多车时详情应再按 rescuer 覆盖）
            order.setAcceptedAt(primary.getAcceptedAt());
            order.setCheckedInAt(primary.getCheckedInAt());
            order.setCheckinLng(primary.getCheckinLng());
            order.setCheckinLat(primary.getCheckinLat());
            order.setCheckinMode(primary.getCheckinMode());
            order.setCheckinRemark(primary.getCheckinRemark());
            order.setRejectReason(primary.getRejectReason());
        }
        if (displayVehicleId != null) {
            RescueVehicle vehicle = rescueVehicleMapper.findById(displayVehicleId);
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
        if (displayRescuerId != null) {
            User user = userMapper.findById(displayRescuerId);
            if (user != null) {
                order.setRescuerName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            }
        }
    }

    /** Overlay assignment process fields onto order for the given rescuer (mobile detail). */
    public void projectAssignmentOntoOrder(DispatchOrder order, Long rescuerId) {
        if (order == null || rescuerId == null) {
            return;
        }
        DispatchVehicleAssignment a = assignmentMapper.findByOrderIdAndRescuerId(order.getId(), rescuerId);
        if (a == null) {
            return;
        }
        order.setVehicleId(a.getVehicleId());
        order.setRescuerId(a.getRescuerId());
        order.setAcceptedAt(a.getAcceptedAt());
        order.setCheckedInAt(a.getCheckedInAt());
        order.setCheckinLng(a.getCheckinLng());
        order.setCheckinLat(a.getCheckinLat());
        order.setCheckinMode(a.getCheckinMode());
        order.setCheckinRemark(a.getCheckinRemark());
        order.setRejectReason(a.getRejectReason());
        if (a.getVehiclePlate() != null) {
            order.setVehiclePlate(a.getVehiclePlate());
        } else if (a.getVehicleId() != null) {
            RescueVehicle v = rescueVehicleMapper.findById(a.getVehicleId());
            if (v != null) {
                order.setVehiclePlate(v.getPlateNo());
            }
        }
    }
}
