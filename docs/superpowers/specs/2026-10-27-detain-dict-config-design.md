# 入库登记字典配置（扣押部门 / 施救方式）设计

日期：2026-10-27
状态：已确认，待写实现计划

## 1. 背景与目标

救援员移动端「入库登记」（`mobile-rescuer/src/pages/task/park.vue`）的**扣押部门**、**施救方式**目前是自由文本输入（`<input>`），管理员无法统一维护，录入内容随意、难以统计。

目标：把这两项改成后台配置的字典。管理员在 PC 端可以新增、编辑、启用/停用、删除字典项；移动端入库页与 PC 扣留车辆页都从字典取选项。

## 2. 不做什么

- 不改「施救原因」（`ACCIDENT/ILLEGAL/RESCUE`），它仍是固定枚举。
- 不改 `detained_vehicle` 表结构：`detain_dept`、`rescue_method` 仍是 `VARCHAR(100)` 存文本，字典只是选项来源。
- 不做「已被引用就禁止删除」的约束：历史记录存的是文本快照，删字典项不影响既有数据展示。
- 不做字典项的多语言、层级、颜色等扩展字段。

## 3. 数据模型

### 3.1 新表 `sys_dict_item`

```sql
CREATE TABLE `sys_dict_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(32) NOT NULL COMMENT '字典类型：DETAIN_DEPT/RESCUE_METHOD',
  `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `sort_order` INT DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_name` (`dict_type`, `name`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典项';
```

- 一个类型下名称唯一；不同类型允许同名。
- `status` 仅 `ENABLED` / `DISABLED`。

### 3.2 字典类型枚举

| dict_type | 含义 | 用于 |
|---|---|---|
| `DETAIN_DEPT` | 扣押部门 | `detained_vehicle.detain_dept` |
| `RESCUE_METHOD` | 施救方式 | `detained_vehicle.rescue_method` |

类型在后端硬编码为白名单常量集合，未知类型直接拒绝。

### 3.3 种子数据

存量数据里的 `DADAD`、`DASDSADA` 等脏值不迁移。种子只放规范值：

| dict_type | name | sort_order |
|---|---|---|
| DETAIN_DEPT | 福田交警大队 | 1 |
| DETAIN_DEPT | 南山交警大队 | 2 |
| DETAIN_DEPT | 罗湖交警大队 | 3 |
| DETAIN_DEPT | 宝安交警大队 | 4 |
| DETAIN_DEPT | 城北中队 | 5 |
| DETAIN_DEPT | 城南中队 | 6 |
| RESCUE_METHOD | 拖车 | 1 |
| RESCUE_METHOD | 清障车 | 2 |
| RESCUE_METHOD | 代驾 | 3 |
| RESCUE_METHOD | 自开 | 4 |

存量记录中的其他文本值（如「城北大队」）继续保留在 `detained_vehicle` 中，不做回填或清洗。

## 4. 权限

新增权限（`permission` 表 id 72–76），挂在新的模块「字典管理」下：

| id | permission_name | permission_code | type | parent_id | sort_order |
|---|---|---|---|---|---|
| 72 | 字典管理 | `dict:manage` | MODULE | 0 | 15 |
| 73 | 字典查询 | `dict:query` | BUTTON | 72 | 1 |
| 74 | 字典新增 | `dict:add` | BUTTON | 72 | 2 |
| 75 | 字典编辑 | `dict:edit` | BUTTON | 72 | 3 |
| 76 | 字典删除 | `dict:delete` | BUTTON | 72 | 4 |

只授予系统管理员（`role_id = 5`）。`init.sql` 中 role 5 的授权区间补 `OR id BETWEEN 72 AND 76`。

## 5. 后端

### 5.1 文件清单

| 动作 | 文件 |
|---|---|
| 新增 | `entity/SysDictItem.java` |
| 新增 | `mapper/SysDictItemMapper.java` |
| 新增 | `dto/DictItemRequest.java` |
| 新增 | `service/SysDictService.java` |
| 新增 | `controller/SysDictController.java` |
| 新增 | `test/.../service/SysDictServiceTest.java` |

### 5.2 接口契约

| 方法 | 路径 | 权限 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/dict/list` | `dict:query` | `type*`、`status?`、`page=1`、`size=10` | `Result<Map{list,total,page,size}>` |
| GET | `/api/dict/enabled` | 登录即可 | `type*` | `Result<List<SysDictItem>>` |
| POST | `/api/dict` | `dict:add` | `{dictType,name,sortOrder,status,remark}` | `Result<Void>` |
| PUT | `/api/dict/{id}` | `dict:edit` | `{name,sortOrder,status,remark}`（`dictType` 不可改） | `Result<Void>` |
| DELETE | `/api/dict/{id}` | `dict:delete` | — | `Result<Void>` |

- `/enabled` 不加 `@PreAuthorize`，与既有 `/api/vehicle-type/enabled` 一致：救援员移动端需要读取，已由 JWT 过滤器保证必须登录。
- 控制器统一 `try/catch RuntimeException → Result.error(e.getMessage())`，与仓库既有风格一致。

### 5.3 业务校验（`SysDictService`）

- `requireType(type)`：必须是 `DETAIN_DEPT` 或 `RESCUE_METHOD`，否则 `RuntimeException("字典类型无效")`。
- 新增：名称 trim 后为空 → `"字典名称不能为空"`；同类型同名 → `"字典名称已存在"`。
- 更新：条目不存在 → `"字典项不存在"`；改名称时同样查同类型重名（排除自身）。
- 删除：条目不存在 → `"字典项不存在"`。
- `status` 只允许 `ENABLED` / `DISABLED`，新增不传默认 `ENABLED`。
- `listEnabled(type)`：过滤 `ENABLED`，按 `sortOrder` 升序、`id` 升序。

## 6. PC 前端

### 6.1 文件清单

| 动作 | 文件 |
|---|---|
| 新增 | `frontend/src/api/dict.js` |
| 新增 | `frontend/src/views/dict/DictList.vue` |
| 修改 | `frontend/src/router/index.js`（`/dicts`，meta `dict:manage`） |
| 修改 | `frontend/src/App.vue`（侧边栏「字典管理」、`pageTitle` 映射） |
| 修改 | `frontend/src/views/detain/DetainedVehicleList.vue` |

### 6.2 字典管理页 `DictList.vue`

- 顶部两个 tab：`扣押部门`（DETAIN_DEPT）、`施救方式`（RESCUE_METHOD）。切 tab 重置分页并重新拉取。
- 工具栏：状态筛选（全部/启用/停用）+ 查询/重置 + 「新增」按钮（`v-auth="'dict:add'"`）。
- 表格列：名称、排序、状态徽标、备注、操作。
- 操作列：`编辑`（`dict:edit`）、`停用/启用`（`dict:edit`）、`删除`（`dict:delete`，`confirm` 二次确认）。
- 弹窗字段：名称（必填）、排序（数字）、状态、备注。新增时 `dictType` 取当前 tab。

### 6.3 扣留车辆页改造

- **编辑弹窗**：「扣留部门」「施救方式」由 `<input>` 改为 `<select>`，选项 = 字典 `ENABLED` 项；若当前记录的值不在其中，把该值补为第一项，避免编辑时被清空。数据加载时机：`openEdit` 时并行拉取两个类型的 enabled 列表（可缓存在模块级变量，页面加载时拉一次即可）。
- **列表筛选**：「扣留部门」由文本框改为 `<select>`，选项 = 「全部」+ 字典 `DETAIN_DEPT` 项。提交给后端的仍是字符串，后端 `LIKE` 查询对完整名称等价于精确匹配。
- 字典拉取失败时降级：下拉为空，编辑弹窗保留当前值（仍可保存原值）。

## 7. 移动端

### 7.1 文件清单

| 动作 | 文件 |
|---|---|
| 修改 | `mobile-rescuer/src/api/rescuer.js`（新增 `listDictOptions(type)`） |
| 修改 | `mobile-rescuer/src/pages/task/park.vue` |

### 7.2 `park.vue` 改动

- 第二步「扣押部门」「施救方式」由 `<input>` 改为 `<picker mode="selector">`，展示文案与既有停车场 picker 一致（`picker-value` + `placeholder` 样式）。
- `onLoad` 中与停车场、任务详情并行加载两个字典：`DETAIN_DEPT`、`RESCUE_METHOD`。
- 字典为空时显示「暂无配置」，两项保留为**选填**，不阻塞「下一步」与提交。
- 提交时若已选值，仍按字符串写入 `detainDept` / `rescueMethod`，后端契约不变。

## 8. 测试

### 8.1 单元测试 `SysDictServiceTest`

覆盖：

1. 非法 `dictType` → 抛「字典类型无效」。
2. 新增名称为空/纯空格 → 抛「字典名称不能为空」。
3. 同类型重名 → 抛「字典名称已存在」。
4. 不同类型同名 → 允许（mapper 查询按 `(type, name)` 命中，返回 `null` 时通过）。
5. 更新不存在的条目 → 抛「字典项不存在」。
6. 删除不存在 → 抛「字典项不存在」。
7. `listEnabled` 过滤 `DISABLED` 并按 `sortOrder` 升序。
8. 非法 `status` → 抛「字典状态仅允许 ENABLED 或 DISABLED」。

### 8.2 手工验证

1. 管理员登录 PC → 字典管理 → 新增「福田交警二大队」→ 列表出现。
2. 救援员移动端任务详情 → 入库登记 → 第二步「扣押部门」下拉出现该项。
3. 提交入库 → PC 扣留车辆列表该记录「扣留部门」显示该值；筛选下拉可选中并查出该记录。
4. 管理员停用该项 → 移动端下拉不再出现；PC 编辑该记录时下拉仍显示原值。
5. 管理员删除该项 → 移动端下拉消失；已有记录展示不受影响。

## 9. 风险与取舍

- **删除是物理删除**：管理员误删需重新添加。已通过二次确认 + 历史数据存文本缓解。
- **PC 筛选从模糊改精确**：原文本框支持关键词模糊（如输入「大队」匹配多条）。改下拉后只能整项匹配，符合「字典化」的预期，但弱化了模糊能力；若后续仍需要模糊，可保留文本框并在下方给出候选。
- **字典项顺序**：由管理员维护 `sortOrder`，移动端与 PC 下拉都按它排序，保证同一项在两处位置一致。
