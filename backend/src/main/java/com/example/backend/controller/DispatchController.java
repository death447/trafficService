package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.AbortDispatchRequest;
import com.example.backend.dto.AssignDispatchRequest;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.DispatchOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dispatch")
@CrossOrigin(origins = "*")
public class DispatchController {

    @Autowired
    private DispatchOrderService dispatchOrderService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('dispatch:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Long dispatcherId) {

        List<DispatchOrder> orders = dispatchOrderService.list(orderNo, status, address, dispatcherId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", orders);
        result.put("total", orders.size());
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('dispatch:query')")
    public Result<DispatchOrder> getById(@PathVariable Long id) {
        DispatchOrder order = dispatchOrderService.findById(id);
        if (order == null) {
            return Result.error("工单不存在");
        }
        return Result.success(order);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('dispatch:add')")
    public Result<DispatchOrder> create(@RequestBody DispatchOrder order) {
        try {
            Long dispatcherId = currentUserId();
            boolean success = dispatchOrderService.create(order, dispatcherId);
            if (success) {
                return Result.success(order);
            }
            return Result.error("创建工单失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('dispatch:edit')")
    public Result<DispatchOrder> update(@PathVariable Long id, @RequestBody DispatchOrder order) {
        order.setId(id);
        try {
            boolean success = dispatchOrderService.update(order);
            if (success) {
                return Result.success(dispatchOrderService.findById(id));
            }
            return Result.error("更新工单失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('dispatch:dispatch')")
    public Result<DispatchOrder> assign(@PathVariable Long id, @Valid @RequestBody AssignDispatchRequest request) {
        try {
            dispatchOrderService.assign(id, request.getVehicleId(), request.getRescuerId());
            return Result.success(dispatchOrderService.findById(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('dispatch:complete')")
    public Result<DispatchOrder> complete(@PathVariable Long id) {
        try {
            dispatchOrderService.complete(id);
            return Result.success(dispatchOrderService.findById(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/abort")
    @PreAuthorize("hasAuthority('dispatch:abort')")
    public Result<DispatchOrder> abort(@PathVariable Long id, @Valid @RequestBody AbortDispatchRequest request) {
        try {
            dispatchOrderService.abort(id, request.getAbortReason());
            return Result.success(dispatchOrderService.findById(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private Long currentUserId() {
        CustomUserDetails principal =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getId();
    }
}
