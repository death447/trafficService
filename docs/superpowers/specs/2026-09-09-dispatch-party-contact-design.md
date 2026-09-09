# 工单事故当事人与联系方式设计文档

## 概述

在救援工单上增加选填字段**事故当事人**与**手机号码（联系方式）**。管理端新建可填、待派单详情可改；列表在「施救车辆」之后展示两列；施救员移动端任务详情只读展示。

本轮采用：`dispatch_order` 直接增加 `party_name`、`party_phone` 两列（与车牌字段同一套路）。

## 背景与范围

### 已有基础
- `dispatch_order`：地点、原因、车牌、车型快照、调度员/车辆/施救员、状态机已就绪
- 管理端：`DispatchCreate` / `DispatchList` / `DispatchDetail`（PENDING 可编辑车牌车型）
- 移动端：任务详情展示工单字段；现场采集另有独立现场记录

### 需求决议
| 项 | 决议 |
|----|------|
| 必填性 | 当事人、手机号均为**选填** |
| 管理端录入 | 新建可填；**仅 PENDING** 详情可编辑（同车牌/车型） |
| 列表列序 | … → 施救车辆 → **事故当事人** → **联系方式** → 车牌 → 车型 → … |
| 移动端 | 任务详情**只读**展示；不可改 |
| 存储 | 工单表两列，不建独立当事人表 |
| 手机校验 | 本轮仅长度上限；**不做**强正则 |

### 本轮目标
1. DB：`party_name`、`party_phone` + migrate / `init.sql`
2. 后端 Entity/Mapper/create/update 读写；列表详情带出
3. 管理端新建、列表、详情（编辑+只读）
4. 移动端任务详情只读两行

### 明确不做
- 独立当事人表 / JSON 塞备注
- 手机号正则强制、短信、外呼
- 列表按当事人/电话筛选（可后续）
- 现场记录回写或与现场采集字段打通
- 已派单及之后状态改当事人（非 PENDING 不可改）

## 技术方案

### 总体架构

| 模块 | 改动 |
|------|------|
| DB | `dispatch_order.party_name` / `party_phone` |
| 后端 | `DispatchOrder` + Mapper insert/update；create/update trim 空串→null |
| 管理端 | Create / List / Detail |
| 移动端 | `pages/task/detail` 只读展示 |

**数据流：**
1. 调度新建可选填当事人、手机 → 写入工单  
2. PENDING 详情可改并保存  
3. 列表/详情/移动端详情读取展示  

## 数据库设计

```sql
ALTER TABLE `dispatch_order`
  ADD COLUMN `party_name` VARCHAR(50) DEFAULT NULL COMMENT '事故当事人' AFTER `vehicle_type_name`,
  ADD COLUMN `party_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式/手机号码' AFTER `party_name`;
```

- `init.sql` 同步 DDL  
- migrate：`database/migrate_2026-09-09_dispatch_party_contact.sql`

## 后端设计

- `DispatchOrder`：`partyName`、`partyPhone`
- Mapper insert/update 含两列
- create/update：`trim`；空串 → `null`；超长截断或拒绝（建议超长抛业务错误或前端 maxlength）
- PENDING `update` 允许修改（与现有工单更新一致）
- 无新权限码

## 管理端前端

### 新建 `DispatchCreate.vue`
- 表单增加「事故当事人」「手机号码」，`maxlength` 50 / 20

### 列表 `DispatchList.vue`
列序：

单号 | 地点 | 派单时间 | 调度员 | 施救员 | **施救车辆** | **事故当事人** | **联系方式** | 车牌 | 车型 | 状态 | 创建时间 | 操作

### 详情 `DispatchDetail.vue`
- 只读信息区展示当事人、联系方式  
- PENDING 编辑区（与车牌车型同面板或相邻）可改并保存  

## 移动端

- `pages/task/detail`：只读展示「事故当事人」「联系方式」；无值显示「—」  
- 不改现场采集 / 入库页  

## 测试与验收

| # | 标准 |
|---|------|
| 1 | 新建可不填提交；可填后详情/列表可见 |
| 2 | PENDING 可改并保存；非 PENDING 无编辑入口 |
| 3 | 列表列序：施救车辆 → 事故当事人 → 联系方式 → 车牌 |
| 4 | 移动端任务详情只读可见 |
| 5 | 空串入库为 null |

## 后续衔接（非本轮）
- 列表按当事人/电话筛选  
- 手机号格式校验  
- 移动端可改（若业务需要）
