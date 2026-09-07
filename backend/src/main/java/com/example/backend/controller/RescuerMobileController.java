package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.BindVehicleRequest;
import com.example.backend.dto.CheckinRequest;
import com.example.backend.dto.ParkRequest;
import com.example.backend.dto.RejectRequest;
import com.example.backend.dto.RescuerProfileUpdateRequest;
import com.example.backend.dto.RescuerTaskDetail;
import com.example.backend.dto.SceneRequest;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.User;
import com.example.backend.mapper.UserMapper;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.DispatchOrderService;
import com.example.backend.service.RescuerMobileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/mobile/rescuer")
@CrossOrigin(origins = "*")
public class RescuerMobileController {

    @Autowired
    private RescuerMobileService rescuerMobileService;

    @Autowired
    private DispatchOrderService dispatchOrderService;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('rescuer:profile')")
    public Result<User> getProfile() {
        User user = userMapper.findById(currentUserId());
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('rescuer:profile')")
    public Result<User> updateProfile(@RequestBody RescuerProfileUpdateRequest request) {
        try {
            rescuerMobileService.updateProfile(currentUserId(), request);
            return Result.success(userMapper.findById(currentUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/bind-vehicle")
    @PreAuthorize("hasAuthority('rescuer:bind-vehicle')")
    public Result<RescueVehicle> bindVehicle(@RequestBody BindVehicleRequest request) {
        try {
            return Result.success(rescuerMobileService.bindVehicle(currentUserId(), request));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/vehicle")
    @PreAuthorize("hasAuthority('rescuer:bind-vehicle')")
    public Result<RescueVehicle> getBoundVehicle() {
        return Result.success(rescuerMobileService.getBoundVehicle(currentUserId()));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('rescuer:task')")
    public Result<List<DispatchOrder>> listTasks(@RequestParam(defaultValue = "todo") String tab) {
        try {
            return Result.success(rescuerMobileService.listTasks(currentUserId(), tab));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('rescuer:task')")
    public Result<RescuerTaskDetail> getTask(@PathVariable Long id) {
        try {
            return Result.success(rescuerMobileService.getTask(currentUserId(), id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/accept")
    @PreAuthorize("hasAuthority('rescuer:accept')")
    public Result<DispatchOrder> accept(@PathVariable Long id) {
        try {
            Long userId = currentUserId();
            dispatchOrderService.accept(id, userId);
            return Result.success(rescuerMobileService.getTask(userId, id).getOrder());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/reject")
    @PreAuthorize("hasAuthority('rescuer:reject')")
    public Result<DispatchOrder> reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        try {
            String reason = request != null ? request.getReason() : null;
            dispatchOrderService.reject(id, currentUserId(), reason);
            return Result.success(dispatchOrderService.findById(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/checkin")
    @PreAuthorize("hasAuthority('rescuer:checkin')")
    public Result<DispatchOrder> checkin(@PathVariable Long id, @RequestBody CheckinRequest request) {
        try {
            Long userId = currentUserId();
            if (request == null) {
                return Result.error("签到参数无效");
            }
            dispatchOrderService.checkin(id, userId, request.getLng(), request.getLat(),
                    request.getMode(), request.getRemark());
            return Result.success(rescuerMobileService.getTask(userId, id).getOrder());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/scene")
    @PreAuthorize("hasAuthority('rescuer:scene')")
    public Result<Void> saveScene(@PathVariable Long id, @RequestBody SceneRequest request) {
        try {
            rescuerMobileService.saveScene(currentUserId(), id, request);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/park")
    @PreAuthorize("hasAuthority('rescuer:park')")
    public Result<Void> savePark(@PathVariable Long id, @RequestBody ParkRequest request) {
        try {
            rescuerMobileService.savePark(currentUserId(), id, request);
            return Result.success(null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/media")
    @PreAuthorize("hasAuthority('rescuer:scene') or hasAuthority('rescuer:park')")
    public Result<DispatchMedia> addMedia(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestParam String bizType) {
        try {
            requireMediaAuthority(bizType);
            return Result.success(rescuerMobileService.addMedia(currentUserId(), id, bizType, file));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/tasks/{id}/media")
    @PreAuthorize("hasAuthority('rescuer:task')")
    public Result<List<DispatchMedia>> listMedia(@PathVariable Long id) {
        try {
            return Result.success(rescuerMobileService.listMedia(currentUserId(), id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/complete")
    @PreAuthorize("hasAuthority('rescuer:complete')")
    public Result<DispatchOrder> complete(@PathVariable Long id) {
        try {
            Long userId = currentUserId();
            rescuerMobileService.getTask(userId, id);
            dispatchOrderService.complete(id);
            return Result.success(rescuerMobileService.getTask(userId, id).getOrder());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private void requireMediaAuthority(String bizType) {
        String required = "DAMAGE".equals(bizType) ? "rescuer:scene"
                : "PARK".equals(bizType) ? "rescuer:park" : null;
        if (required == null) {
            throw new RuntimeException("媒体类型无效，仅支持 DAMAGE/PARK");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = auth.getAuthorities().stream()
                .anyMatch(a -> required.equals(a.getAuthority()));
        if (!allowed) {
            throw new RuntimeException("无权上传该类型媒体");
        }
    }

    private Long currentUserId() {
        CustomUserDetails principal =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getId();
    }
}
