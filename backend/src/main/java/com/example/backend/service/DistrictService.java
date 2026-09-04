package com.example.backend.service;

import com.example.backend.dto.DistrictRequest;
import com.example.backend.dto.LngLat;
import com.example.backend.entity.District;
import com.example.backend.mapper.DistrictMapper;
import com.example.backend.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DistrictService {

    private static final String MSG_REF_DELETE =
            "片区仍被车辆或排班引用，请先解绑或改为禁用";

    @Autowired
    private DistrictMapper districtMapper;

    public List<District> list(String keyword, String status) {
        return districtMapper.findAll().stream()
                .filter(d -> {
                    if (keyword != null && !keyword.isEmpty()) {
                        String name = d.getName() != null ? d.getName() : "";
                        String code = d.getCode() != null ? d.getCode() : "";
                        if (!name.contains(keyword) && !code.contains(keyword)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isEmpty()) {
                        if (!status.equals(d.getStatus())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    public District findById(Long id) {
        return districtMapper.findById(id);
    }

    @Transactional
    public boolean create(DistrictRequest req) {
        District district = new District();
        applyRequest(district, req, true);
        ensureCodeUnique(district.getCode(), null);
        return districtMapper.insert(district) > 0;
    }

    @Transactional
    public boolean update(Long id, DistrictRequest req) {
        District existing = districtMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("片区不存在");
        }
        applyRequest(existing, req, false);
        ensureCodeUnique(existing.getCode(), id);
        return districtMapper.update(existing) > 0;
    }

    @Transactional
    public boolean delete(Long id) {
        District existing = districtMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("片区不存在");
        }
        if (districtMapper.countVehiclesByDistrictId(id) > 0
                || districtMapper.countSchedulesByDistrictId(id) > 0) {
            throw new RuntimeException(MSG_REF_DELETE);
        }
        return districtMapper.deleteById(id) > 0;
    }

    public District resolve(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            throw new RuntimeException("经纬度不能为空");
        }
        double x = lng.doubleValue();
        double y = lat.doubleValue();
        return districtMapper.findByStatus("ENABLED").stream()
                .sorted(Comparator.comparing(District::getId))
                .filter(d -> {
                    List<LngLat> polygon = GeoUtils.normalizeFence(GeoUtils.parseFence(d.getFenceJson()));
                    return GeoUtils.contains(polygon, x, y);
                })
                .findFirst()
                .orElse(null);
    }

    private void applyRequest(District district, DistrictRequest req, boolean creating) {
        if (req.getName() != null) {
            district.setName(req.getName());
        }
        if (req.getCode() != null) {
            district.setCode(req.getCode());
        }
        district.setFenceJson(resolveFenceJson(req));
        String status = req.getStatus();
        if (status == null || status.isEmpty()) {
            if (creating) {
                district.setStatus("ENABLED");
            }
        } else {
            if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
                throw new RuntimeException("片区状态仅允许 ENABLED 或 DISABLED");
            }
            district.setStatus(status);
        }
        if (req.getRemark() != null) {
            district.setRemark(req.getRemark());
        }
    }

    private String resolveFenceJson(DistrictRequest req) {
        List<LngLat> points;
        if (req.getFence() != null) {
            points = GeoUtils.normalizeFence(req.getFence());
        } else if (req.getFenceJson() != null && !req.getFenceJson().isEmpty()) {
            points = GeoUtils.normalizeFence(GeoUtils.parseFence(req.getFenceJson()));
        } else {
            throw new RuntimeException("围栏至少需要3个顶点");
        }
        return GeoUtils.toFenceJson(points);
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        District byCode = districtMapper.findByCode(code);
        if (byCode != null && (excludeId == null || !byCode.getId().equals(excludeId))) {
            throw new RuntimeException("片区编码已存在");
        }
    }
}
