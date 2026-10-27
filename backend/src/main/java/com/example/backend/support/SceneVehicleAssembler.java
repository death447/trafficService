package com.example.backend.support;

import com.example.backend.dto.SceneVehicleVO;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchSceneVehicle;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchSceneVehicleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把「工单逐车现场记录 + 照片」组装成详情接口返回的 {@link SceneVehicleVO} 列表。
 */
@Component
public class SceneVehicleAssembler {

    private static final String BIZ_DAMAGE = "DAMAGE";

    @Autowired
    private DispatchSceneVehicleMapper sceneVehicleMapper;

    @Autowired
    private DispatchMediaMapper mediaMapper;

    public List<SceneVehicleVO> assemble(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        List<DispatchSceneVehicle> rows = sceneVehicleMapper.findByOrderId(orderId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DispatchMedia>> mediasByVehicle = new LinkedHashMap<>();
        List<DispatchMedia> medias = mediaMapper.findByOrderIdAndBizType(orderId, BIZ_DAMAGE);
        if (medias != null) {
            for (DispatchMedia m : medias) {
                if (m == null || m.getAccidentVehicleId() == null) {
                    continue;
                }
                mediasByVehicle.computeIfAbsent(m.getAccidentVehicleId(), k -> new ArrayList<>()).add(m);
            }
        }
        List<SceneVehicleVO> out = new ArrayList<>(rows.size());
        for (DispatchSceneVehicle row : rows) {
            SceneVehicleVO vo = new SceneVehicleVO();
            vo.setId(row.getId());
            vo.setAccidentVehicleId(row.getAccidentVehicleId());
            vo.setPlateNo(row.getPlateNo());
            vo.setVehicleType(row.getVehicleType());
            vo.setDamageDesc(row.getDamageDesc());
            vo.setMedias(mediasByVehicle.getOrDefault(row.getAccidentVehicleId(), new ArrayList<>()));
            out.add(vo);
        }
        return out;
    }
}
