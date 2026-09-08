package com.example.backend.service;

import com.example.backend.dto.VehicleTypeRequest;
import com.example.backend.entity.AccidentVehicleType;
import com.example.backend.mapper.AccidentVehicleTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccidentVehicleTypeService {

    private static final String MSG_NOT_ENABLED = "车型不存在或已停用";

    @Autowired
    private AccidentVehicleTypeMapper mapper;

    public List<AccidentVehicleType> list(String status) {
        return mapper.findAll().stream()
                .filter(row -> {
                    if (status != null && !status.isEmpty()) {
                        return status.equals(row.getStatus());
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    public List<AccidentVehicleType> listEnabled() {
        return mapper.findAll().stream()
                .filter(row -> "ENABLED".equals(row.getStatus()))
                .sorted(Comparator
                        .comparing(AccidentVehicleType::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AccidentVehicleType::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public AccidentVehicleType findById(Long id) {
        return mapper.findById(id);
    }

    public AccidentVehicleType requireEnabled(Long id) {
        AccidentVehicleType row = mapper.findById(id);
        if (row == null || !"ENABLED".equals(row.getStatus())) {
            throw new RuntimeException(MSG_NOT_ENABLED);
        }
        return row;
    }

    @Transactional
    public boolean create(VehicleTypeRequest req) {
        String name = req.getName() != null ? req.getName().trim() : "";
        if (name.isEmpty()) {
            throw new RuntimeException("车型名称不能为空");
        }
        ensureNameUnique(name, null);

        AccidentVehicleType row = new AccidentVehicleType();
        row.setName(name);
        row.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        applyStatus(row, req.getStatus(), true);
        if (req.getRemark() != null) {
            row.setRemark(req.getRemark());
        }
        return mapper.insert(row) > 0;
    }

    @Transactional
    public boolean update(Long id, VehicleTypeRequest req) {
        AccidentVehicleType existing = mapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("车型不存在");
        }
        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty()) {
                throw new RuntimeException("车型名称不能为空");
            }
            ensureNameUnique(name, id);
            existing.setName(name);
        }
        if (req.getSortOrder() != null) {
            existing.setSortOrder(req.getSortOrder());
        }
        applyStatus(existing, req.getStatus(), false);
        if (req.getRemark() != null) {
            existing.setRemark(req.getRemark());
        }
        return mapper.update(existing) > 0;
    }

    private void applyStatus(AccidentVehicleType row, String status, boolean creating) {
        if (status == null || status.isEmpty()) {
            if (creating) {
                row.setStatus("ENABLED");
            }
        } else {
            if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
                throw new RuntimeException("车型状态仅允许 ENABLED 或 DISABLED");
            }
            row.setStatus(status);
        }
    }

    private void ensureNameUnique(String name, Long excludeId) {
        AccidentVehicleType byName = mapper.findByName(name);
        if (byName != null && (excludeId == null || !byName.getId().equals(excludeId))) {
            throw new RuntimeException("车型名称已存在");
        }
    }
}
