package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.PoliceTaskDetail;
import com.example.backend.dto.PoliceTaskItem;
import com.example.backend.dto.RateTaskRequest;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchOrderEvaluation;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public DispatchOrderEvaluation rate(Long userId, Long orderId, RateTaskRequest request) {
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("仅已完成工单可评价");
        }
        if (evaluationMapper.findByOrderId(orderId) != null) {
            throw new RuntimeException("该工单已评价");
        }
        if (request == null
                || !validScore(request.getScorePunctual())
                || !validScore(request.getScoreStandard())
                || !validScore(request.getScoreSafety())
                || !validScore(request.getScoreAttitude())) {
            throw new RuntimeException("每个维度须为 1 至 5 星");
        }
        String comment = request.getComment();
        if (comment != null) {
            comment = comment.trim();
            if (comment.isEmpty()) {
                comment = null;
            } else if (comment.length() > 500) {
                throw new RuntimeException("反馈意见不能超过500字");
            }
        }
        DispatchOrderEvaluation row = new DispatchOrderEvaluation();
        row.setDispatchOrderId(orderId);
        row.setRaterUserId(userId);
        row.setScorePunctual(request.getScorePunctual());
        row.setScoreStandard(request.getScoreStandard());
        row.setScoreSafety(request.getScoreSafety());
        row.setScoreAttitude(request.getScoreAttitude());
        row.setComment(comment);
        try {
            evaluationMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("该工单已评价");
        }
        return row;
    }

    private boolean validScore(Integer score) {
        return score != null && score >= 1 && score <= 5;
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
