package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.DictItemRequest;
import com.example.backend.entity.SysDictItem;
import com.example.backend.mapper.SysDictItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysDictService {

    public static final String TYPE_DETAIN_DEPT = "DETAIN_DEPT";
    public static final String TYPE_RESCUE_METHOD = "RESCUE_METHOD";

    private static final Set<String> TYPES = Set.of(TYPE_DETAIN_DEPT, TYPE_RESCUE_METHOD);

    @Autowired
    private SysDictItemMapper mapper;

    public Map<String, Object> list(String dictType, String status, PageParams pp) {
        String type = requireType(dictType);
        long total = mapper.count(type, status);
        List<SysDictItem> rows = mapper.selectPage(type, status, pp.getOffset(), pp.getSize());
        return pp.toResult(rows, total);
    }

    public List<SysDictItem> listEnabled(String dictType) {
        String type = requireType(dictType);
        return mapper.findByType(type).stream()
                .filter(row -> "ENABLED".equals(row.getStatus()))
                .sorted(Comparator
                        .comparing(SysDictItem::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SysDictItem::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public SysDictItem findById(Long id) {
        return mapper.findById(id);
    }

    @Transactional
    public boolean create(DictItemRequest req) {
        String type = requireType(req.getDictType());
        String name = req.getName() != null ? req.getName().trim() : "";
        if (name.isEmpty()) {
            throw new RuntimeException("字典名称不能为空");
        }
        ensureNameUnique(type, name, null);

        SysDictItem row = new SysDictItem();
        row.setDictType(type);
        row.setName(name);
        row.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        applyStatus(row, req.getStatus(), true);
        if (req.getRemark() != null) {
            row.setRemark(req.getRemark());
        }
        return mapper.insert(row) > 0;
    }

    @Transactional
    public boolean update(Long id, DictItemRequest req) {
        SysDictItem existing = mapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("字典项不存在");
        }
        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty()) {
                throw new RuntimeException("字典名称不能为空");
            }
            ensureNameUnique(existing.getDictType(), name, id);
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

    @Transactional
    public boolean delete(Long id) {
        SysDictItem existing = mapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("字典项不存在");
        }
        return mapper.deleteById(id) > 0;
    }

    public String requireType(String dictType) {
        String type = dictType != null ? dictType.trim() : "";
        if (!TYPES.contains(type)) {
            throw new RuntimeException("字典类型无效");
        }
        return type;
    }

    private void applyStatus(SysDictItem row, String status, boolean creating) {
        if (status == null || status.isEmpty()) {
            if (creating) {
                row.setStatus("ENABLED");
            }
        } else {
            if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
                throw new RuntimeException("字典状态仅允许 ENABLED 或 DISABLED");
            }
            row.setStatus(status);
        }
    }

    private void ensureNameUnique(String dictType, String name, Long excludeId) {
        SysDictItem byName = mapper.findByTypeAndName(dictType, name);
        if (byName != null && (excludeId == null || !byName.getId().equals(excludeId))) {
            throw new RuntimeException("字典名称已存在");
        }
    }
}
