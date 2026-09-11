package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.common.Result;
import com.example.backend.dto.PoliceTaskDetail;
import com.example.backend.dto.RateTaskRequest;
import com.example.backend.entity.DispatchOrderEvaluation;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.PoliceMobileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mobile/police")
@CrossOrigin(origins = "*")
public class PoliceMobileController {

    @Autowired
    private PoliceMobileService policeMobileService;

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('accident:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageParams pp = PageParams.normalize(page, size);
        return Result.success(policeMobileService.list(pp));
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('accident:query')")
    public Result<PoliceTaskDetail> getTask(@PathVariable Long id) {
        try {
            return Result.success(policeMobileService.getTask(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/rate")
    @PreAuthorize("hasAuthority('accident:rate')")
    public Result<DispatchOrderEvaluation> rate(@PathVariable Long id, @RequestBody RateTaskRequest request) {
        try {
            return Result.success(policeMobileService.rate(currentUserId(), id, request));
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
