package com.example.backend.service;

import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.User;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    public boolean create(DispatchOrder order, Long dispatcherId) {
        order.setOrderNo(generateOrderNo());
        order.setStatus("PENDING");
        order.setDispatcherId(dispatcherId);
        order.setVehicleId(null);
        order.setRescuerId(null);
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
        return dispatchOrderMapper.update(existing) > 0;
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
    public void complete(Long orderId) {
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"DISPATCHED".equals(order.getStatus())) {
            throw new RuntimeException("仅已派单状态可完成");
        }
        Long vehicleId = order.getVehicleId();
        order.setStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        dispatchOrderMapper.update(order);
        releaseVehicleIfUnused(vehicleId);
    }

    @Transactional
    public void abort(Long orderId, String abortReason) {
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"PENDING".equals(order.getStatus()) && !"DISPATCHED".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不可作废");
        }
        Long vehicleId = order.getVehicleId();
        boolean wasDispatched = "DISPATCHED".equals(order.getStatus());
        order.setStatus("ABORTED");
        order.setAbortReason(abortReason);
        dispatchOrderMapper.update(order);
        if (wasDispatched) {
            releaseVehicleIfUnused(vehicleId);
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
