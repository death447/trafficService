# 任务管理列表单元格不换行设计

## 概述

优化管理端「任务管理」列表表格展示：短字段单行显示；「地点」过长时省略并悬停查看全文，避免单元格换行导致行高参差、版面杂乱。

## 背景与范围

### 现状
- 页面：`frontend/src/views/dispatch/DispatchList.vue`
- 表头已有全局 `white-space: nowrap`（`enterprise.css`）
- 单元格（`td`）未限制换行；列较多（含车牌、车型）时，「地点」等长文本易折行

### 目标
1. 短字段单元格不换行
2. 「地点」单行省略 + `title` 展示完整地址
3. 表格容器可横向滚动，避免撑破布局

### 明确不做
- 筛选区布局改动
- 修改全局 `enterprise.css` 的 `.data-table` 默认行为（避免影响其他列表页）
- 详情页、新建页
- 合并列或改字段含义

## 技术方案

### 改动文件
仅 `frontend/src/views/dispatch/DispatchList.vue`

### 实现要点

1. **表格容器**  
   在现有 `.panel` 内表格外包一层（如 `.table-scroll`），设置 `overflow-x: auto`。

2. **短字段**  
   scoped 样式：`.data-table th, .data-table td { white-space: nowrap; }`  
   覆盖列：单号、派单时间、调度员、施救员、施救车辆、车牌、车型、状态、创建时间、操作。

3. **地点列**  
   - 单元格增加 class（如 `cell-address`）  
   - 样式：`max-width: 14rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;`  
   - 绑定 `:title`：有地址时为完整字符串，空值可不设或设为「—」对应文案

4. **操作列**  
   保持现有 `.actions` 的 `nowrap` / flex 同行按钮，无需额外业务逻辑。

### 验收
- 列表加载后，除「地点」过长被省略外，其余可见字段不换行
- 鼠标悬停「地点」省略单元格可看到完整地址
- 窄视口下表格可横向滚动，页面主体不横向溢出

## 决议
采用方案 B：短字段 nowrap + 地点 ellipsis/`title` + 表格横向滚动。
