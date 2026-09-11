package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.PoliceTaskDetail;
import com.example.backend.dto.PoliceTaskItem;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PoliceMobileService {

    @Autowired
    private DispatchOrderMapper dispatchOrderMapper;
    @Autowired
    private DispatchFieldRecordMapper fieldRecordMapper;
    @Autowired
    private DispatchMediaMapper mediaMapper;
    @Autowired
    private DispatchOrderEvaluationMapper evaluationMapper;

    public Map<String, Object> list(PageParams pp) {
        long total = dispatchOrderMapper.count(null, null, null, null);
        List<DispatchOrder> orders = dispatchOrderMapper.selectPage(
                null, null, null, null, pp.getOffset(), pp.getSize());
        Set<Long> rated = ratedIds(orders.stream().map(DispatchOrder::getId).collect(Collectors.toList()));
        List<PoliceTaskItem> items = orders.stream()
                .map(o -> toItem(o, rated.contains(o.getId())))
                .collect(Collectors.toList());
        return pp.toResult(items, total);
    }

    public PoliceTaskDetail getTask(Long id) {
        DispatchOrder order = dispatchOrderMapper.findById(id);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        PoliceTaskDetail detail = new PoliceTaskDetail();
        detail.setOrder(order);
        detail.setFieldRecord(fieldRecordMapper.findByOrderId(id));
        List<DispatchMedia> medias = mediaMapper.findByOrderId(id);
        detail.setMedias(medias == null ? List.of() : medias);
        detail.setEvaluation(evaluationMapper.findByOrderId(id));
        return detail;
    }

    private Set<Long> ratedIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> found = evaluationMapper.findOrderIds(ids);
        return found == null ? Collections.emptySet() : new HashSet<>(found);
    }

    private PoliceTaskItem toItem(DispatchOrder o, boolean rated) {
        PoliceTaskItem item = new PoliceTaskItem();
        item.setId(o.getId());
        item.setOrderNo(o.getOrderNo());
        item.setStatus(o.getStatus());
        item.setAccidentAddress(o.getAccidentAddress());
        item.setPlateNo(o.getPlateNo());
        item.setPartyName(o.getPartyName());
        item.setPartyPhone(o.getPartyPhone());
        item.setCreateTime(o.getCreateTime());
        item.setRated(rated);
        return item;
    }
}
