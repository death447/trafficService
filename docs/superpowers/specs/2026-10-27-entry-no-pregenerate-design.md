# 入库入场编码页面预生成 — 设计文档

## 概述

移动端救援员「入库登记」第三步的「入场编号」原来是只读的「提交后生成」。改为进入页面时由后端预生成一个候选编码并在页面上展示，提交时优先使用该编码；若恰好与已有记录重复，后端自动换一个新码，不打断提交。

## 已确认决策

| 项 | 选择 |
|----|------|
| 时间部分 | `yyyyMMddHH`（年月日时，10 位） |
| 订单因子 | 关联工单 ID，补零到 4 位，超过 4 位取后 4 位 |
| 随机部分 | 5 位随机数字 |
| 总长度 | 19 位纯数字 |
| 提交策略 | 优先用页面预生成的；重复或非法则后端重新生成 |

示例：工单 `17`，2026-10-27 14 点 → `2026102714` + `0017` + `48291` = `2026102714001748291`

## 背景

- 生成逻辑现状：`DetainedVehicleService.generateEntryNo()` 用 `yyyyMMdd` + 当日序号 4 位（`countByEntryNoPrefix` +1），例如 `202610270001`。
- 移动端现状：`mobile-rescuer/src/pages/task/park.vue` 第三步「入场编号」渲染「提交后生成」占位文本。
- 入库链路：`POST /api/mobile/rescuer/tasks/{id}/detain-in` → `RescuerMobileService.detainIn` → `DetainedVehicleService.checkIn`。
- 表约束：`detained_vehicle.entry_no VARCHAR(32)`，唯一索引 `uk_entry_no`。

## 接口设计

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/mobile/rescuer/tasks/{id}/entry-no` | `rescuer:park` | 返回一个候选入场编码（不落库、不占号） |

响应：`Result<String>`，`data` 即 19 位编码。

校验：工单必须存在且当前用户是该工单的已接单救援员（复用 `requireAssignment`）。校验失败返回 `Result.error`。

## 生成规则

新增 `com.example.backend.support.EntryNoGenerator`（Spring `@Component`）：

- `String generate(Long orderId)`：`now().format("yyyyMMddHH")` + 4 位工单因子 + 5 位随机数字（`SecureRandom`）
- `boolean matches(String code, Long orderId)`：正则 `^\d{19}$`，且第 11–14 位等于归一化后的工单因子
- 工单因子：`String.format("%04d", orderId % 10000)`
- `orderId` 为 null 时因子用 `0000`（PC 直接调 `/api/detained-vehicle` 的兼容路径）

## 入库逻辑改造

`DetainInRequest` 增加 `entryNo` 字段。`DetainedVehicleService.checkIn` 的编码赋值改为：

```
String entryNo = resolveEntryNo(req.getEntryNo(), req.getDispatchOrderId());
```

`resolveEntryNo(candidate, orderId)`：

1. `candidate` 非空 且 `generator.matches(candidate, orderId)` 且 `detainedVehicleMapper.findByEntryNo(candidate) == null` → 返回 `candidate`
2. 否则循环最多 5 次：`generator.generate(orderId)`，若 `findByEntryNo` 为空则返回
3. 5 次仍冲突 → 抛 `RuntimeException("入场编号生成失败，请重试")`

`DetainedVehicleMapper` 新增 `@Select("SELECT * FROM detained_vehicle WHERE entry_no = #{entryNo}") DetainedVehicle findByEntryNo(String entryNo)`。

删除 `generateEntryNo()` 与 `countByEntryNoPrefix` 的调用。`countByEntryNoPrefix` 方法本身一并删除（无其他调用方）。

## 移动端改造

`mobile-rescuer/src/pages/task/park.vue`：

- `onLoad` 与任务详情并行请求 `GET /tasks/{id}/entry-no`，结果存 `entryNo`
- 第三步「入场编号」展示 `entryNo || '提交后生成'`；加载失败不报错、不阻塞
- 提交 `detain-in` 时带上 `entryNo`
- 接口封装加到 `mobile-rescuer/src/api/rescuer.js`

PC 端不改：列表与编辑弹窗已展示 `entryNo`。历史编码（如 `202609170001`）继续正常展示，不做数据迁移。

## 测试

后端：

- `EntryNoGeneratorTest`：长度 19 且全数字、前 10 位为当前小时、工单因子位正确、`orderId=10017` 取后 4 位、多次生成随机位不同、`matches` 对错误长度/错误因子返回 false
- `DetainedVehicleServiceTest`：传入合法 `entryNo` 被采用；传入非法格式回退生成；传入已被占用的编码回退生成
- `RescuerMobileServiceTest`：`entryNo` 透传到 `checkIn`；`GET entry-no` 校验 assignment 归属

手工：

1. 救援员进入入库登记，第三步显示 19 位编码，前缀与当前小时一致
2. 提交后用该编码查库，与页面展示一致
3. 构造重复编码提交，仍能入库且编码被换成新值

## 明确不做

- 不加「重新生成」按钮
- 不做编码段位（流水号）或分布式发号器
- 不改历史数据、不改 PC 页面
