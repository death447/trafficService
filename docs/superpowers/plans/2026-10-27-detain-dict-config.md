# 入库登记字典配置（扣押部门 / 施救方式）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把入库登记里的「扣押部门」「施救方式」从自由文本改成后台字典，管理员可在 PC 端增删改停用，救援员移动端与 PC 扣留车辆页从字典取选项。

**Architecture:** 新增通用字典表 `sys_dict_item`，用 `dict_type` 区分 `DETAIN_DEPT` / `RESCUE_METHOD`；一套 Mapper/Service/Controller 服务两个字典。PC 端新增「字典管理」页（两个 tab），移动端入库页把两个 `<input>` 换成 `<picker>`，PC 扣留车辆页把编辑弹窗与筛选换成 `<select>`。`detained_vehicle` 表结构不变，历史数据仍是文本快照。

**Tech Stack:** Spring Boot 3.2 + Java 17 + MyBatis + Spring Security；Vue 3 + Vite + Element Plus-less 自研组件；Uni-App Vue 3。

## Global Constraints

- 设计文档：`docs/superpowers/specs/2026-10-27-detain-dict-config-design.md`（本计划的唯一需求来源）。
- 字典类型白名单固定为 `DETAIN_DEPT` 与 `RESCUE_METHOD`，其它值一律拒绝。
- 状态值只有 `ENABLED` / `DISABLED`。
- 同一 `dict_type` 下名称唯一；不同 `dict_type` 允许同名。
- 不改 `detained_vehicle` 表结构，不迁移/清洗历史脏值（`DADAD`、`DASDSADA` 保留原样）。
- 后端测试命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=<TestClass> test`
- 后端全量测试命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q test`
- PC 构建命令（在 `frontend`）：`npm run build`
- 移动端构建命令（在 `mobile-rescuer`）：`npm run build:h5`
- 数据库连接（本机）：`C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe`，库名 `vue_springboot_system`，账号 `root`，密码 `minth@888`。
- 注释、界面文案用中文；Java 类名、字段名用英文，与既有代码风格一致。

---

### Task 1: 数据库表、种子数据与权限

**Files:**
- Modify: `database/init.sql:1-20`（DROP 区）
- Modify: `database/init.sql:181-192`（`accident_vehicle_type` 建表块之后）
- Modify: `database/init.sql:424-431`（权限 INSERT 末尾）
- Modify: `database/init.sql:455-459`（ADMIN 角色授权）
- Modify: `database/init.sql:433-452`（车型种子之后）
- Create: `database/migrate_2026-10-27_dict_config.sql`
- Test: 直接对本地 MySQL 查询验证

**Interfaces:**
- Produces: 表 `sys_dict_item(id, dict_type, name, sort_order, status, remark, create_time, update_time)`；权限码 `dict:manage` / `dict:query` / `dict:add` / `dict:edit` / `dict:delete`（id 72–76）。

- [ ] **Step 1: 在 `init.sql` 的 DROP 区加入新表**

把第 12 行 `DROP TABLE IF EXISTS `accident_vehicle_type`;` 后面补一行，改成：

```sql
DROP TABLE IF EXISTS `accident_vehicle_type`;
DROP TABLE IF EXISTS `sys_dict_item`;
```

- [ ] **Step 2: 在 `accident_vehicle_type` 建表块之后加入 `sys_dict_item` 建表语句**

在 `init.sql` 的 `COMMENT='事故车型字典';` 这一行之后、`CREATE TABLE `dispatch_field_record`` 之前插入：

```sql
CREATE TABLE `sys_dict_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(32) NOT NULL COMMENT '字典类型：DETAIN_DEPT/RESCUE_METHOD',
  `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_name` (`dict_type`, `name`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典项';
```

- [ ] **Step 3: 追加权限行**

把 `init.sql` 中这一行：

```sql
(71, '报表查询', 'report:query', 'BUTTON', 70, 1);
```

替换为：

```sql
(71, '报表查询', 'report:query', 'BUTTON', 70, 1),
(72, '字典管理', 'dict:manage', 'MODULE', 0, 15),
(73, '字典查询', 'dict:query', 'BUTTON', 72, 1),
(74, '字典新增', 'dict:add', 'BUTTON', 72, 2),
(75, '字典编辑', 'dict:edit', 'BUTTON', 72, 3),
(76, '字典删除', 'dict:delete', 'BUTTON', 72, 4);
```

- [ ] **Step 4: 给 ADMIN 角色授权**

把：

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id IN (16, 17, 19)
   OR id BETWEEN 20 AND 66
   OR id IN (68, 69, 70, 71);
```

替换为：

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id IN (16, 17, 19)
   OR id BETWEEN 20 AND 66
   OR id IN (68, 69, 70, 71)
   OR id BETWEEN 72 AND 76;
```

- [ ] **Step 5: 追加种子数据**

在 `init.sql` 中 `INSERT INTO `accident_vehicle_type` ...;` 整条语句结束之后插入：

```sql
INSERT INTO `sys_dict_item` (`dict_type`, `name`, `sort_order`, `status`) VALUES
('DETAIN_DEPT', '福田交警大队', 1, 'ENABLED'),
('DETAIN_DEPT', '南山交警大队', 2, 'ENABLED'),
('DETAIN_DEPT', '罗湖交警大队', 3, 'ENABLED'),
('DETAIN_DEPT', '宝安交警大队', 4, 'ENABLED'),
('DETAIN_DEPT', '城北中队', 5, 'ENABLED'),
('DETAIN_DEPT', '城南中队', 6, 'ENABLED'),
('RESCUE_METHOD', '拖车', 1, 'ENABLED'),
('RESCUE_METHOD', '清障车', 2, 'ENABLED'),
('RESCUE_METHOD', '代驾', 3, 'ENABLED'),
('RESCUE_METHOD', '自开', 4, 'ENABLED');
```

- [ ] **Step 6: 新建幂等迁移脚本**

创建 `database/migrate_2026-10-27_dict_config.sql`：

```sql
-- 入库登记的扣押部门 / 施救方式改为后台字典配置
CREATE TABLE IF NOT EXISTS `sys_dict_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(32) NOT NULL COMMENT '字典类型：DETAIN_DEPT/RESCUE_METHOD',
  `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_name` (`dict_type`, `name`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典项';

INSERT IGNORE INTO `sys_dict_item` (`dict_type`, `name`, `sort_order`, `status`) VALUES
('DETAIN_DEPT', '福田交警大队', 1, 'ENABLED'),
('DETAIN_DEPT', '南山交警大队', 2, 'ENABLED'),
('DETAIN_DEPT', '罗湖交警大队', 3, 'ENABLED'),
('DETAIN_DEPT', '宝安交警大队', 4, 'ENABLED'),
('DETAIN_DEPT', '城北中队', 5, 'ENABLED'),
('DETAIN_DEPT', '城南中队', 6, 'ENABLED'),
('RESCUE_METHOD', '拖车', 1, 'ENABLED'),
('RESCUE_METHOD', '清障车', 2, 'ENABLED'),
('RESCUE_METHOD', '代驾', 3, 'ENABLED'),
('RESCUE_METHOD', '自开', 4, 'ENABLED');

INSERT IGNORE INTO `permission`
  (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
(72, '字典管理', 'dict:manage', 'MODULE', 0, 15),
(73, '字典查询', 'dict:query', 'BUTTON', 72, 1),
(74, '字典新增', 'dict:add', 'BUTTON', 72, 2),
(75, '字典编辑', 'dict:edit', 'BUTTON', 72, 3),
(76, '字典删除', 'dict:delete', 'BUTTON', 72, 4);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, p.id FROM `permission` p
WHERE p.id BETWEEN 72 AND 76
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp WHERE rp.role_id = 5 AND rp.permission_id = p.id
  );
```

- [ ] **Step 7: 对本地库执行迁移**

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" "--host=127.0.0.1" "--port=3306" "--user=root" "--password=minth@888" "--protocol=tcp" "--default-character-set=utf8mb4" vue_springboot_system -e "source d:/cursorworkspace/database/migrate_2026-10-27_dict_config.sql"
```

Expected: 无输出、退出码 0。

- [ ] **Step 8: 验证表与数据**

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" "--host=127.0.0.1" "--port=3306" "--user=root" "--password=minth@888" "--protocol=tcp" "--default-character-set=utf8mb4" vue_springboot_system -e "SELECT dict_type, COUNT(*) FROM sys_dict_item GROUP BY dict_type; SELECT COUNT(*) AS admin_dict_perms FROM role_permission WHERE role_id=5 AND permission_id BETWEEN 72 AND 76;"
```

Expected:

```
dict_type       COUNT(*)
DETAIN_DEPT            6
RESCUE_METHOD          4
admin_dict_perms      5
```

- [ ] **Step 9: 提交**

```powershell
git add database/init.sql database/migrate_2026-10-27_dict_config.sql
git commit -m "feat: add sys_dict_item table, seeds and dict permissions"
```

---

### Task 2: 后端字典服务

**Files:**
- Create: `backend/src/main/java/com/example/backend/entity/SysDictItem.java`
- Create: `backend/src/main/java/com/example/backend/mapper/SysDictItemMapper.java`
- Create: `backend/src/main/java/com/example/backend/dto/DictItemRequest.java`
- Create: `backend/src/main/java/com/example/backend/service/SysDictService.java`
- Test: `backend/src/test/java/com/example/backend/service/SysDictServiceTest.java`

**Interfaces:**
- Consumes: 表 `sys_dict_item`（Task 1）。
- Produces:
  - `SysDictItem`（字段 `id, dictType, name, sortOrder, status, remark, createTime, updateTime`）
  - `DictItemRequest`（字段 `dictType, name, sortOrder, status, remark`）
  - `SysDictService.list(String dictType, String status, PageParams pp) → Map<String,Object>`
  - `SysDictService.listEnabled(String dictType) → List<SysDictItem>`
  - `SysDictService.findById(Long id) → SysDictItem`
  - `SysDictService.create(DictItemRequest req) → boolean`
  - `SysDictService.update(Long id, DictItemRequest req) → boolean`
  - `SysDictService.delete(Long id) → boolean`
  - `SysDictService.requireType(String dictType) → String`
  - 常量 `SysDictService.TYPE_DETAIN_DEPT = "DETAIN_DEPT"`、`TYPE_RESCUE_METHOD = "RESCUE_METHOD"`

- [ ] **Step 1: 创建实体**

`backend/src/main/java/com/example/backend/entity/SysDictItem.java`：

```java
package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysDictItem {
    private Long id;
    private String dictType;
    private String name;
    private Integer sortOrder;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 创建 Mapper**

`backend/src/main/java/com/example/backend/mapper/SysDictItemMapper.java`：

```java
package com.example.backend.mapper;

import com.example.backend.entity.SysDictItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysDictItemMapper {

    @Select("SELECT * FROM sys_dict_item WHERE dict_type = #{dictType}")
    List<SysDictItem> findByType(String dictType);

    @Select("SELECT * FROM sys_dict_item WHERE id = #{id}")
    SysDictItem findById(Long id);

    @Select("SELECT * FROM sys_dict_item WHERE dict_type = #{dictType} AND name = #{name}")
    SysDictItem findByTypeAndName(@Param("dictType") String dictType, @Param("name") String name);

    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_dict_item WHERE dict_type = #{dictType}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</script>")
    long count(@Param("dictType") String dictType, @Param("status") String status);

    @Select("<script>" +
            "SELECT * FROM sys_dict_item WHERE dict_type = #{dictType}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            " ORDER BY sort_order ASC, id ASC LIMIT #{offset}, #{size}" +
            "</script>")
    List<SysDictItem> selectPage(@Param("dictType") String dictType,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    @Insert("INSERT INTO sys_dict_item (dict_type, name, sort_order, status, remark) " +
            "VALUES (#{dictType}, #{name}, #{sortOrder}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysDictItem row);

    @Update("UPDATE sys_dict_item SET name=#{name}, sort_order=#{sortOrder}, " +
            "status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(SysDictItem row);

    @Delete("DELETE FROM sys_dict_item WHERE id = #{id}")
    int deleteById(Long id);
}
```

- [ ] **Step 3: 创建请求 DTO**

`backend/src/main/java/com/example/backend/dto/DictItemRequest.java`：

```java
package com.example.backend.dto;

import lombok.Data;

@Data
public class DictItemRequest {
    private String dictType;
    private String name;
    private Integer sortOrder;
    private String status;
    private String remark;
}
```

- [ ] **Step 4: 创建服务骨架（让测试能编译）**

`backend/src/main/java/com/example/backend/service/SysDictService.java`：

```java
package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.DictItemRequest;
import com.example.backend.entity.SysDictItem;
import com.example.backend.mapper.SysDictItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SysDictService {

    public static final String TYPE_DETAIN_DEPT = "DETAIN_DEPT";
    public static final String TYPE_RESCUE_METHOD = "RESCUE_METHOD";

    @Autowired
    private SysDictItemMapper mapper;

    public Map<String, Object> list(String dictType, String status, PageParams pp) {
        throw new UnsupportedOperationException("not implemented");
    }

    public List<SysDictItem> listEnabled(String dictType) {
        throw new UnsupportedOperationException("not implemented");
    }

    public SysDictItem findById(Long id) {
        return mapper.findById(id);
    }

    public boolean create(DictItemRequest req) {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean update(Long id, DictItemRequest req) {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean delete(Long id) {
        throw new UnsupportedOperationException("not implemented");
    }

    public String requireType(String dictType) {
        throw new UnsupportedOperationException("not implemented");
    }
}
```

- [ ] **Step 5: 写失败测试**

`backend/src/test/java/com/example/backend/service/SysDictServiceTest.java`：

```java
package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.DictItemRequest;
import com.example.backend.entity.SysDictItem;
import com.example.backend.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysDictServiceTest {

    @Mock SysDictItemMapper mapper;
    @InjectMocks SysDictService service;

    private DictItemRequest request(String dictType, String name) {
        DictItemRequest req = new DictItemRequest();
        req.setDictType(dictType);
        req.setName(name);
        return req;
    }

    private SysDictItem item(String name, int sortOrder, String status) {
        SysDictItem row = new SysDictItem();
        row.setName(name);
        row.setSortOrder(sortOrder);
        row.setStatus(status);
        return row;
    }

    @Test
    void listRejectsUnknownType() {
        assertThrows(RuntimeException.class,
                () -> service.list("UNKNOWN", null, PageParams.normalize(1, 10)));
        verifyNoInteractions(mapper);
    }

    @Test
    void createRejectsBlankName() {
        assertThrows(RuntimeException.class, () -> service.create(request("DETAIN_DEPT", "   ")));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createRejectsDuplicateNameInSameType() {
        when(mapper.findByTypeAndName("DETAIN_DEPT", "福田交警大队"))
                .thenReturn(new SysDictItem());
        assertThrows(RuntimeException.class,
                () -> service.create(request("DETAIN_DEPT", "福田交警大队")));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createAllowsSameNameInDifferentType() {
        when(mapper.insert(any())).thenReturn(1);
        assertTrue(service.create(request("RESCUE_METHOD", "拖车")));
        verify(mapper).findByTypeAndName("RESCUE_METHOD", "拖车");
        verify(mapper).insert(any());
    }

    @Test
    void createRejectsInvalidStatus() {
        DictItemRequest req = request("DETAIN_DEPT", "福田交警大队");
        req.setStatus("ARCHIVED");
        assertThrows(RuntimeException.class, () -> service.create(req));
        verify(mapper, never()).insert(any());
    }

    @Test
    void updateRejectsMissingItem() {
        when(mapper.findById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class,
                () -> service.update(99L, request("DETAIN_DEPT", "X")));
    }

    @Test
    void updateRejectsDuplicateName() {
        SysDictItem existing = item("福田交警大队", 1, "ENABLED");
        existing.setId(1L);
        existing.setDictType("DETAIN_DEPT");
        SysDictItem other = item("南山交警大队", 2, "ENABLED");
        other.setId(2L);
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.findByTypeAndName("DETAIN_DEPT", "南山交警大队")).thenReturn(other);
        assertThrows(RuntimeException.class,
                () -> service.update(1L, request("DETAIN_DEPT", "南山交警大队")));
        verify(mapper, never()).update(any());
    }

    @Test
    void deleteRejectsMissingItem() {
        when(mapper.findById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.delete(99L));
        verify(mapper, never()).deleteById(any());
    }

    @Test
    void deleteRemovesExistingItem() {
        SysDictItem existing = item("拖车", 1, "ENABLED");
        existing.setId(1L);
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.deleteById(1L)).thenReturn(1);
        assertTrue(service.delete(1L));
        verify(mapper).deleteById(1L);
    }

    @Test
    void listEnabledFiltersAndSorts() {
        SysDictItem a = item("清障车", 2, "ENABLED");
        SysDictItem b = item("拖车", 1, "ENABLED");
        SysDictItem c = item("旧方式", 0, "DISABLED");
        when(mapper.findByType("RESCUE_METHOD")).thenReturn(List.of(a, b, c));
        List<SysDictItem> list = service.listEnabled("RESCUE_METHOD");
        assertEquals(2, list.size());
        assertEquals("拖车", list.get(0).getName());
        assertEquals("清障车", list.get(1).getName());
    }
}
```

- [ ] **Step 6: 运行测试确认失败**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=SysDictServiceTest test
```

Expected: FAIL，`UnsupportedOperationException: not implemented`（`listRejectsUnknownType` 等多条失败）。

- [ ] **Step 7: 实现服务**

把 `SysDictService.java` 整体替换为：

```java
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
```

- [ ] **Step 8: 运行测试确认通过**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=SysDictServiceTest test
```

Expected: PASS，`Tests run: 10, Failures: 0, Errors: 0`。

- [ ] **Step 9: 提交**

```powershell
git add backend/src/main/java/com/example/backend/entity/SysDictItem.java backend/src/main/java/com/example/backend/mapper/SysDictItemMapper.java backend/src/main/java/com/example/backend/dto/DictItemRequest.java backend/src/main/java/com/example/backend/service/SysDictService.java backend/src/test/java/com/example/backend/service/SysDictServiceTest.java
git commit -m "feat: add sys dict service with validations"
```

---

### Task 3: 后端字典接口

**Files:**
- Create: `backend/src/main/java/com/example/backend/controller/SysDictController.java`
- Test: `backend/src/test/java/com/example/backend/controller/SysDictControllerTest.java`

**Interfaces:**
- Consumes: `SysDictService`（Task 2）。
- Produces: HTTP 接口 `GET /api/dict/list`、`GET /api/dict/enabled`、`POST /api/dict`、`PUT /api/dict/{id}`、`DELETE /api/dict/{id}`。

- [ ] **Step 1: 创建控制器**

`backend/src/main/java/com/example/backend/controller/SysDictController.java`：

```java
package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.common.Result;
import com.example.backend.dto.DictItemRequest;
import com.example.backend.entity.SysDictItem;
import com.example.backend.service.SysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
@CrossOrigin(origins = "*")
public class SysDictController {

    @Autowired
    private SysDictService dictService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('dict:query')")
    public Result<Map<String, Object>> list(
            @RequestParam String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageParams pp = PageParams.normalize(page, size);
        try {
            return Result.success(dictService.list(type, status, pp));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/enabled")
    public Result<List<SysDictItem>> listEnabled(@RequestParam String type) {
        try {
            return Result.success(dictService.listEnabled(type));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('dict:add')")
    public Result<Void> create(@RequestBody DictItemRequest request) {
        try {
            if (dictService.create(request)) {
                return Result.success(null);
            }
            return Result.error("创建字典项失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictItemRequest request) {
        try {
            if (dictService.update(id, request)) {
                return Result.success(null);
            }
            return Result.error("更新字典项失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            if (dictService.delete(id)) {
                return Result.success(null);
            }
            return Result.error("删除字典项失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 写控制器测试**

`backend/src/test/java/com/example/backend/controller/SysDictControllerTest.java`：

```java
package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.entity.SysDictItem;
import com.example.backend.security.CustomUserDetailsService;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.service.SysDictService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SysDictController.class)
@AutoConfigureMockMvc(addFilters = false)
class SysDictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysDictService dictService;
    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void enabledReturnsOptions() throws Exception {
        SysDictItem row = new SysDictItem();
        row.setId(1L);
        row.setDictType("DETAIN_DEPT");
        row.setName("福田交警大队");
        row.setStatus("ENABLED");
        when(dictService.listEnabled("DETAIN_DEPT")).thenReturn(List.of(row));

        mockMvc.perform(get("/api/dict/enabled").param("type", "DETAIN_DEPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("福田交警大队"));
    }

    @Test
    void enabledReturnsErrorForUnknownType() throws Exception {
        when(dictService.listEnabled("NOPE")).thenThrow(new RuntimeException("字典类型无效"));

        mockMvc.perform(get("/api/dict/enabled").param("type", "NOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("字典类型无效"));
    }

    @Test
    void createPassesRequestToService() throws Exception {
        when(dictService.create(any())).thenReturn(true);

        mockMvc.perform(post("/api/dict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dictType":"RESCUE_METHOD","name":"拖车","sortOrder":1,\
                                "status":"ENABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(dictService).create(any());
    }

    @Test
    void listUsesNormalizedPageParams() throws Exception {
        when(dictService.list(eq("DETAIN_DEPT"), eq("ENABLED"), any(PageParams.class)))
                .thenReturn(Map.of("list", List.of(), "total", 0, "page", 1, "size", 10));

        mockMvc.perform(get("/api/dict/list")
                        .param("type", "DETAIN_DEPT")
                        .param("status", "ENABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
```

- [ ] **Step 3: 运行控制器测试**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=SysDictControllerTest test
```

Expected: PASS，`Tests run: 4, Failures: 0, Errors: 0`。

- [ ] **Step 4: 跑全量后端测试**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q test
```

Expected: BUILD SUCCESS，无失败用例。

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/example/backend/controller/SysDictController.java backend/src/test/java/com/example/backend/controller/SysDictControllerTest.java
git commit -m "feat: add sys dict REST endpoints"
```

---

### Task 4: PC 字典管理页

**Files:**
- Create: `frontend/src/api/dict.js`
- Create: `frontend/src/views/dict/DictList.vue`
- Modify: `frontend/src/router/index.js:97-103`
- Modify: `frontend/src/App.vue:120-128`（侧边栏）
- Modify: `frontend/src/App.vue:190-193`（pageTitle 映射）

**Interfaces:**
- Consumes: `GET/POST/PUT/DELETE /api/dict*`（Task 3）。
- Produces:
  - `listDictItems(params)`、`listEnabledDict(type)`、`createDictItem(data)`、`updateDictItem(id, data)`、`deleteDictItem(id)`
  - 路由 `/dicts`，权限 `dict:manage`

- [ ] **Step 1: 创建 API 封装**

`frontend/src/api/dict.js`：

```js
import request from '../utils/request'

export function listDictItems(params) {
  return request.get('/dict/list', { params })
}

export function listEnabledDict(type) {
  return request.get('/dict/enabled', { params: { type } })
}

export function createDictItem(data) {
  return request.post('/dict', data)
}

export function updateDictItem(id, data) {
  return request.put(`/dict/${id}`, data)
}

export function deleteDictItem(id) {
  return request.delete(`/dict/${id}`)
}
```

- [ ] **Step 2: 创建字典管理页**

`frontend/src/views/dict/DictList.vue`：

```vue
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>字典管理</h1>
        <p class="subtitle">维护入库登记可选的扣押部门与施救方式</p>
      </div>
      <button v-auth="'dict:add'" type="button" @click="openCreate">新增字典项</button>
    </div>

    <div class="panel tabs">
      <button
        v-for="tab in tabs"
        :key="tab.type"
        type="button"
        :class="['tab-btn', { active: activeType === tab.type }]"
        @click="switchTab(tab.type)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div class="panel filters">
      <label>
        状态
        <select v-model="filters.status">
          <option value="">全部</option>
          <option value="ENABLED">启用</option>
          <option value="DISABLED">停用</option>
        </select>
      </label>
      <div class="filter-actions">
        <button type="button" @click="onQuery">查询</button>
        <button type="button" class="secondary" @click="resetFilters">重置</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>
    <div v-else class="panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>排序</th>
            <th>状态</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ row.sortOrder ?? '—' }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(row.status)]">
                {{ statusLabel(row.status) }}
              </span>
            </td>
            <td>{{ row.remark || '—' }}</td>
            <td class="actions">
              <button v-auth="'dict:edit'" type="button" @click="openEdit(row)">编辑</button>
              <button v-auth="'dict:edit'" type="button" @click="toggleStatus(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </button>
              <button
                v-auth="'dict:delete'"
                type="button"
                class="secondary"
                @click="onDelete(row)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="5" class="empty-cell">暂无字典项</td>
          </tr>
        </tbody>
      </table>
    </div>
    <PaginationBar
      v-if="!loading"
      :page="pagination.page"
      :size="pagination.size"
      :total="pagination.total"
      @update:page="onPageChange"
      @update:size="onSizeChange"
    />

    <div v-if="formVisible" class="modal" @click.self="formVisible = false">
      <form class="modal-card" @submit.prevent="onSubmit">
        <h2>{{ editingId ? '编辑字典项' : `新增${activeTabLabel}` }}</h2>
        <label>
          名称
          <input v-model.trim="form.name" required />
        </label>
        <label>
          排序
          <input v-model.number="form.sortOrder" type="number" min="0" />
        </label>
        <label>
          状态
          <select v-model="form.status" required>
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        <label>
          备注
          <input v-model.trim="form.remark" />
        </label>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="formVisible = false">取消</button>
          <button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  listDictItems,
  createDictItem,
  updateDictItem,
  deleteDictItem
} from '../../api/dict'
import PaginationBar from '../../components/PaginationBar.vue'

const tabs = [
  { type: 'DETAIN_DEPT', label: '扣押部门' },
  { type: 'RESCUE_METHOD', label: '施救方式' }
]

const activeType = ref('DETAIN_DEPT')
const items = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)

const filters = reactive({
  status: ''
})

const form = reactive({
  name: '',
  sortOrder: 0,
  status: 'ENABLED',
  remark: ''
})

const activeTabLabel = computed(
  () => tabs.find((tab) => tab.type === activeType.value)?.label || '字典项'
)

const statusLabels = {
  ENABLED: '启用',
  DISABLED: '停用'
}

function statusLabel(status) {
  return statusLabels[status] || status || '—'
}

function statusBadgeClass(status) {
  if (status === 'ENABLED') return 'badge-success'
  return 'badge-muted'
}

function resetForm() {
  form.name = ''
  form.sortOrder = 0
  form.status = 'ENABLED'
  form.remark = ''
  formError.value = ''
}

function switchTab(type) {
  if (activeType.value === type) return
  activeType.value = type
  pagination.page = 1
  loadList()
}

function resetFilters() {
  filters.status = ''
  pagination.page = 1
  loadList()
}

function onQuery() {
  pagination.page = 1
  loadList()
}

function onPageChange(p) {
  pagination.page = p
  loadList()
}

function onSizeChange(s) {
  pagination.size = s
  pagination.page = 1
  loadList()
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const params = { type: activeType.value, page: pagination.page, size: pagination.size }
    if (filters.status) params.status = filters.status
    const res = await listDictItems(params)
    items.value = res.data?.list || []
    pagination.total = res.data?.total ?? 0
    if (res.data?.page) pagination.page = res.data.page
    if (res.data?.size) pagination.size = res.data.size
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载字典失败'
    items.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  resetForm()
  form.name = row.name || ''
  form.sortOrder = row.sortOrder ?? 0
  form.status = row.status || 'ENABLED'
  form.remark = row.remark || ''
  formVisible.value = true
}

function payload() {
  return {
    dictType: activeType.value,
    name: form.name,
    sortOrder: form.sortOrder ?? 0,
    status: form.status,
    remark: form.remark || null
  }
}

async function onSubmit() {
  formError.value = ''
  saving.value = true
  try {
    if (editingId.value) {
      await updateDictItem(editingId.value, payload())
    } else {
      await createDictItem(payload())
    }
    formVisible.value = false
    await loadList()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  error.value = ''
  try {
    await updateDictItem(row.id, {
      name: row.name,
      sortOrder: row.sortOrder ?? 0,
      status: next,
      remark: row.remark || null
    })
    await loadList()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '状态更新失败'
  }
}

async function onDelete(row) {
  if (!window.confirm(`确认删除「${row.name}」？删除后不可恢复。`)) return
  error.value = ''
  try {
    await deleteDictItem(row.id)
    if (items.value.length === 1 && pagination.page > 1) {
      pagination.page -= 1
    }
    await loadList()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '删除失败'
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.tabs {
  display: flex;
  gap: 0.5rem;
  padding: 0.6rem;
  margin-bottom: 1rem;
}

.tab-btn {
  background: transparent;
  border: 1px solid var(--border-strong);
  color: var(--text-secondary);
  padding: 0.45rem 1rem;
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 0.875rem;
}

.tab-btn.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem 1rem;
  align-items: flex-end;
  padding: 0.9rem 1rem;
  margin-bottom: 1rem;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
  min-width: 140px;
}

.filters select {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: 0.875rem;
  color: var(--text);
  background: #fff;
}

.filter-actions {
  display: flex;
  gap: 0.5rem;
  padding-bottom: 0.05rem;
}

.empty-cell {
  text-align: center;
  color: var(--text-secondary);
  padding: 1.25rem 0.75rem;
}
</style>
```

- [ ] **Step 3: 注册路由**

把 `frontend/src/router/index.js` 里这段：

```js
  {
    path: '/vehicle-types',
    name: 'VehicleTypeList',
    component: () => import('../views/vehicleType/VehicleTypeList.vue'),
    meta: { permissions: ['vehicle-type:manage'] }
  }
]
```

替换为：

```js
  {
    path: '/vehicle-types',
    name: 'VehicleTypeList',
    component: () => import('../views/vehicleType/VehicleTypeList.vue'),
    meta: { permissions: ['vehicle-type:manage'] }
  },
  {
    path: '/dicts',
    name: 'DictList',
    component: () => import('../views/dict/DictList.vue'),
    meta: { permissions: ['dict:manage'] }
  }
]
```

- [ ] **Step 4: 侧边栏加入口**

在 `frontend/src/App.vue` 的「车型管理」`</router-link>` 之后、`</nav>` 之前插入：

```html
        <router-link
          v-auth="'dict:manage'"
          to="/dicts"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-ico">典</span>
          字典管理
        </router-link>
```

- [ ] **Step 5: 页面标题映射**

把 `frontend/src/App.vue` 的 `'/vehicle-types': '车型管理',` 之后补一行：

```js
    '/vehicle-types': '车型管理',
    '/dicts': '字典管理',
```

- [ ] **Step 6: 构建 PC 前端**

```powershell
Set-Location d:\cursorworkspace\frontend; npm run build
```

Expected: `✓ built in ...`，退出码 0。

- [ ] **Step 7: 提交**

```powershell
git add frontend/src/api/dict.js frontend/src/views/dict/DictList.vue frontend/src/router/index.js frontend/src/App.vue
git commit -m "feat(pc): add dictionary management page"
```

---

### Task 5: PC 扣留车辆页改下拉

**Files:**
- Modify: `frontend/src/views/detain/DetainedVehicleList.vue:45-52`（筛选）
- Modify: `frontend/src/views/detain/DetainedVehicleList.vue:215-236`（编辑弹窗两个字段）
- Modify: `frontend/src/views/detain/DetainedVehicleList.vue:269-280`（imports）
- Modify: `frontend/src/views/detain/DetainedVehicleList.vue:283-320`（状态声明）

**Interfaces:**
- Consumes: `listEnabledDict(type)`（Task 4）。
- Produces: 无对外接口；页面行为变化。

- [ ] **Step 1: 引入字典 API 与状态**

在 `DetainedVehicleList.vue` 的 `<script setup>` 中，把：

```js
import { listParkings, listParkingAreas } from '../../api/parking'
```

改为：

```js
import { listParkings, listParkingAreas } from '../../api/parking'
import { listEnabledDict } from '../../api/dict'
```

- [ ] **Step 2: 声明字典选项状态**

在：

```js
const formAreas = ref([])
const formMedias = ref([])
```

之前插入：

```js
const enabledDetainDepts = ref([])
const enabledRescueMethods = ref([])

const formAreas = ref([])
const formMedias = ref([])
```

紧接着 `formParkingOptions` 的 `computed` 之后追加：

```js
/** Edit: include the record's current text even if it is not in the dictionary. */
const formDetainDeptOptions = computed(() => {
  const names = enabledDetainDepts.value.map((row) => row.name)
  if (form.detainDept && !names.includes(form.detainDept)) {
    return [form.detainDept, ...names]
  }
  return names
})

const formRescueMethodOptions = computed(() => {
  const names = enabledRescueMethods.value.map((row) => row.name)
  if (form.rescueMethod && !names.includes(form.rescueMethod)) {
    return [form.rescueMethod, ...names]
  }
  return names
})
```

- [ ] **Step 3: 加字典加载函数**

在 `loadParkings()` 函数之后插入：

```js
async function loadDictOptions() {
  try {
    const [deptRes, methodRes] = await Promise.all([
      listEnabledDict('DETAIN_DEPT'),
      listEnabledDict('RESCUE_METHOD')
    ])
    enabledDetainDepts.value = deptRes.data || []
    enabledRescueMethods.value = methodRes.data || []
  } catch {
    enabledDetainDepts.value = []
    enabledRescueMethods.value = []
  }
}
```

- [ ] **Step 4: `onMounted` 中加载字典**

把：

```js
onMounted(async () => {
  await loadParkings()
  await loadList()
})
```

改为：

```js
onMounted(async () => {
  await loadParkings()
  await loadDictOptions()
  await loadList()
})
```

- [ ] **Step 5: 筛选「扣留部门」改为下拉**

把：

```html
      <label>
        扣留部门
        <input
          v-model.trim="filters.detainDept"
          placeholder="部门关键词"
          @keyup.enter="onQuery"
        />
      </label>
```

替换为：

```html
      <label>
        扣留部门
        <select v-model="filters.detainDept">
          <option value="">全部</option>
          <option v-for="item in enabledDetainDepts" :key="item.id" :value="item.name">
            {{ item.name }}
          </option>
        </select>
      </label>
```

- [ ] **Step 6: 编辑弹窗「扣留部门」改为下拉**

把：

```html
        <label>
          扣留部门
          <input v-model.trim="form.detainDept" />
        </label>
```

替换为：

```html
        <label>
          扣留部门
          <select v-model="form.detainDept">
            <option value="">未填</option>
            <option v-for="name in formDetainDeptOptions" :key="name" :value="name">
              {{ name }}
            </option>
          </select>
        </label>
```

- [ ] **Step 7: 编辑弹窗「施救方式」改为下拉**

把：

```html
        <label>
          施救方式
          <input v-model.trim="form.rescueMethod" />
        </label>
```

替换为：

```html
        <label>
          施救方式
          <select v-model="form.rescueMethod">
            <option value="">未填</option>
            <option v-for="name in formRescueMethodOptions" :key="name" :value="name">
              {{ name }}
            </option>
          </select>
        </label>
```

- [ ] **Step 8: 构建校验**

```powershell
Set-Location d:\cursorworkspace\frontend; npm run build
```

Expected: 构建成功，退出码 0。

- [ ] **Step 9: 提交**

```powershell
git add frontend/src/views/detain/DetainedVehicleList.vue
git commit -m "feat(pc): use dictionary selects for detain dept and rescue method"
```

---

### Task 6: 移动端入库页改 picker

**Files:**
- Modify: `mobile-rescuer/src/api/rescuer.js`（文件末尾追加）
- Modify: `mobile-rescuer/src/pages/task/park.vue:81-84`（扣押部门）
- Modify: `mobile-rescuer/src/pages/task/park.vue:103-106`（施救方式）
- Modify: `mobile-rescuer/src/pages/task/park.vue:216-231`（imports）
- Modify: `mobile-rescuer/src/pages/task/park.vue:239-249`（状态）
- Modify: `mobile-rescuer/src/pages/task/park.vue:313-318`（onLoad）
- Modify: `mobile-rescuer/src/pages/task/park.vue:390-405`（事件处理）

**Interfaces:**
- Consumes: `GET /api/dict/enabled`（Task 3）。
- Produces: `listDictOptions(type)`（`mobile-rescuer/src/api/rescuer.js`）。

- [ ] **Step 1: 移动端 API 增加字典方法**

在 `mobile-rescuer/src/api/rescuer.js` 末尾追加：

```js
export function listDictOptions(type) {
  return request({ url: '/dict/enabled', data: { type }, showError: false })
}
```

- [ ] **Step 2: 引入 API**

把：

```js
  detainInTask,
  getTaskDetain,
  uploadDetainPhoto
} from '../../api/rescuer'
```

改为：

```js
  detainInTask,
  getTaskDetain,
  uploadDetainPhoto,
  listDictOptions
} from '../../api/rescuer'
```

- [ ] **Step 3: 声明字典状态**

在：

```js
const lots = ref([])
const areas = ref([])
```

之后插入：

```js
const detainDepts = ref([])
const rescueMethods = ref([])
```

- [ ] **Step 4: 声明选项与索引 computed**

在 `selectedAreaName` 的 `computed` 之后插入：

```js
const detainDeptNames = computed(() => detainDepts.value.map((row) => row.name))
const rescueMethodNames = computed(() => rescueMethods.value.map((row) => row.name))
const detainDeptIndex = computed(() => {
  const idx = detainDeptNames.value.indexOf(form.detainDept)
  return idx < 0 ? 0 : idx
})
const rescueMethodIndex = computed(() => {
  const idx = rescueMethodNames.value.indexOf(form.rescueMethod)
  return idx < 0 ? 0 : idx
})
```

- [ ] **Step 5: onLoad 中并行加载字典**

把：

```js
onLoad(async (q) => {
  if (!requirePageAccess('rescuer')) return
  id.value = q.id
  await Promise.all([loadTask(), loadLots(), loadMe(), loadExisting()])
})
```

改为：

```js
onLoad(async (q) => {
  if (!requirePageAccess('rescuer')) return
  id.value = q.id
  await Promise.all([loadTask(), loadLots(), loadMe(), loadExisting(), loadDicts()])
})
```

- [ ] **Step 6: 增加字典加载函数**

在 `loadLots()` 函数之后插入：

```js
async function loadDicts() {
  try {
    const [deptRes, methodRes] = await Promise.all([
      listDictOptions('DETAIN_DEPT'),
      listDictOptions('RESCUE_METHOD')
    ])
    detainDepts.value = deptRes.data || []
    rescueMethods.value = methodRes.data || []
  } catch (_) {
    detainDepts.value = []
    rescueMethods.value = []
  }
}
```

- [ ] **Step 7: 增加选择事件**

在 `onColorPick` 函数之后插入：

```js
function onDetainDeptPick(e) {
  const idx = Number(e.detail.value)
  form.detainDept = detainDeptNames.value[idx] || ''
}

function onRescueMethodPick(e) {
  const idx = Number(e.detail.value)
  form.rescueMethod = rescueMethodNames.value[idx] || ''
}
```

- [ ] **Step 8: 扣押部门文本框改 picker**

把：

```html
        <view class="field">
          <view class="field-label">扣押部门</view>
          <input class="field-input" v-model="form.detainDept" placeholder="请填写扣押部门" />
        </view>
```

替换为：

```html
        <view class="field">
          <view class="field-label">扣押部门</view>
          <picker
            mode="selector"
            :range="detainDeptNames"
            :value="detainDeptIndex"
            :disabled="!detainDeptNames.length"
            @change="onDetainDeptPick"
          >
            <view class="field-input picker-value" :class="{ placeholder: !form.detainDept }">
              {{ form.detainDept || (detainDeptNames.length ? '请选择扣押部门' : '暂无配置') }}
            </view>
          </picker>
        </view>
```

- [ ] **Step 9: 施救方式文本框改 picker**

把：

```html
        <view class="field">
          <view class="field-label">施救方式</view>
          <input class="field-input" v-model="form.rescueMethod" placeholder="请填写施救方式" />
        </view>
```

替换为：

```html
        <view class="field">
          <view class="field-label">施救方式</view>
          <picker
            mode="selector"
            :range="rescueMethodNames"
            :value="rescueMethodIndex"
            :disabled="!rescueMethodNames.length"
            @change="onRescueMethodPick"
          >
            <view class="field-input picker-value" :class="{ placeholder: !form.rescueMethod }">
              {{ form.rescueMethod || (rescueMethodNames.length ? '请选择施救方式' : '暂无配置') }}
            </view>
          </picker>
        </view>
```

- [ ] **Step 10: 构建校验**

```powershell
Set-Location d:\cursorworkspace\mobile-rescuer; npm run build:h5
```

Expected: 构建成功，退出码 0，产物写入 `dist/build/h5`。

- [ ] **Step 11: 提交**

```powershell
git add mobile-rescuer/src/api/rescuer.js mobile-rescuer/src/pages/task/park.vue
git commit -m "feat(mobile): pick detain dept and rescue method from dictionary"
```

---

### Task 7: 端到端验证

**Files:**
- 无代码改动（只做验证；如发现问题回到对应任务修复）

**Interfaces:**
- Consumes: Task 1–6 的全部产物。

- [ ] **Step 1: 启动后端**

```powershell
Set-Location d:\cursorworkspace
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
Start-Process -FilePath ".\.tools\apache-maven\bin\mvn.cmd" -ArgumentList "-f","backend\pom.xml","spring-boot:run" -WindowStyle Hidden
```

等待约 25 秒，然后确认：

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}' -UseBasicParsing | Select-Object -ExpandProperty StatusCode
```

Expected: `200`。

- [ ] **Step 2: 通过接口验证权限与 CRUD**

```powershell
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }

# 新增
Invoke-RestMethod -Uri "http://localhost:8080/api/dict" -Method POST -Headers $headers -ContentType "application/json" -Body '{"dictType":"DETAIN_DEPT","name":"福田交警二大队","sortOrder":7,"status":"ENABLED"}' | ConvertTo-Json -Compress

# 查询并取出 id
$dept = Invoke-RestMethod -Uri "http://localhost:8080/api/dict/enabled?type=DETAIN_DEPT" -Headers $headers
$dept | ConvertTo-Json -Compress
$newId = ($dept.data | Where-Object { $_.name -eq '福田交警二大队' }).id
Write-Output "newId=$newId"

# 停用
Invoke-RestMethod -Uri "http://localhost:8080/api/dict/$newId" -Method PUT -Headers $headers -ContentType "application/json" -Body '{"name":"福田交警二大队","sortOrder":7,"status":"DISABLED"}' | ConvertTo-Json -Compress

# 停用后再查，确认已不在启用列表
(Invoke-RestMethod -Uri "http://localhost:8080/api/dict/enabled?type=DETAIN_DEPT" -Headers $headers).data.name -contains '福田交警二大队'

# 删除
Invoke-RestMethod -Uri "http://localhost:8080/api/dict/$newId" -Method DELETE -Headers $headers | ConvertTo-Json -Compress
```

Expected：
- 新增返回 `{"code":200,...}`。
- `/enabled` 列表里出现「福田交警二大队」，位于末尾（`sortOrder=7`）。
- 停用后 `/enabled` 列表不再包含该项。
- 删除返回 `{"code":200,...}`。

- [ ] **Step 3: 用非管理员账号验证权限隔离**

```powershell
$login2 = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"parkingadmin","password":"admin123"}'
$token2 = $login2.data.token
try {
  Invoke-RestMethod -Uri "http://localhost:8080/api/dict/list?type=DETAIN_DEPT" -Headers @{ Authorization = "Bearer $token2" }
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

Expected: `403`（停车场管理员没有 `dict:query`）。同一 token 请求 `/api/dict/enabled?type=DETAIN_DEPT` 应返回 200 且不含 `dict:*` 权限要求。

- [ ] **Step 4: 启动 PC 前端并浏览器验证**

```powershell
Set-Location d:\cursorworkspace\frontend
Start-Process -FilePath "npm.cmd" -ArgumentList "run","dev" -WindowStyle Hidden
```

等待 8 秒，打开 `http://localhost:5173`，用 `admin / admin123` 登录，然后：

1. 侧边栏出现「字典管理」，点击进入 `/dicts`。
2. 默认 tab「扣押部门」列出 6 条种子数据。
3. 切到「施救方式」列出 4 条。
4. 新增「福田交警二大队」→ 列表出现。
5. 点「停用」→ 状态变「停用」。
6. 点「删除」→ 弹确认框 → 确认后消失。
7. 进入「扣留车辆」页：筛选区「扣留部门」是下拉；点任一条「编辑」，「扣留部门」「施救方式」都是下拉且末项含该记录原值（如 `DADAD`）。

- [ ] **Step 5: 移动端 H5 验证**

```powershell
Set-Location d:\cursorworkspace\mobile-rescuer
Start-Process -FilePath "npm.cmd" -ArgumentList "run","dev:h5" -WindowStyle Hidden
```

等待 15 秒，打开 H5 地址（通常 `http://localhost:5173` 或 `http://localhost:5174`，以终端输出为准），用施救员账号登录，进入任一待入库任务 → 入库登记 → 第二步：

1. 「扣押部门」点击弹出选择器，列出 6 条。
2. 「施救方式」点击弹出选择器，列出 4 条。
3. 选中后输入框显示所选名称。
4. 停用某个字典项后刷新页面，该选项不再出现。

- [ ] **Step 6: 关闭验证进程并提交验证结论**

```powershell
Get-Process node -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
git status --short
```

Expected: 无未提交的源码改动（`backend/target`、`backend/uploads` 等构建产物可忽略）。

- [ ] **Step 7: 推送**

```powershell
git push origin master
```

Expected: 推送成功，5 个提交（Task 1–6）全部到达远端。
