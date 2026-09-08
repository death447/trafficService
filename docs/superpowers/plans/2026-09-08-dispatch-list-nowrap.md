# 任务管理列表单元格不换行 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 任务管理列表短字段不换行；「地点」过长省略并用 `title` 悬停查看全文；表格可横向滚动。

**Architecture:** 仅改 `DispatchList.vue`：表格外包 `.table-scroll`；scoped 样式对 `th`/`td` 设 `nowrap`；地点列用 `.cell-address` 做 ellipsis + `:title`。不改全局 `enterprise.css`。

**Tech Stack:** Vue 3 SFC、现有 Element-free 企业样式（`data-table` / `panel`）

**Spec:** `docs/superpowers/specs/2026-09-08-dispatch-list-nowrap-design.md`

## Global Constraints

- 仅修改 `frontend/src/views/dispatch/DispatchList.vue`
- 不修改 `frontend/src/styles/enterprise.css`
- 不改筛选区、详情页、新建页
- 地点 `max-width` 使用 `14rem`
- 不新增依赖；无自动化单测（纯展示 CSS，用手动验收）

---

## File map

| File | Responsibility |
|------|----------------|
| `frontend/src/views/dispatch/DispatchList.vue` | 表格结构、地点 `title`、scoped 不换行/省略样式 |

---

### Task 1: 列表表格不换行与地点省略

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchList.vue`

**Interfaces:**
- Consumes: 现有 `row.accidentAddress`、`orders` 列表渲染
- Produces: `.table-scroll` 容器、`.cell-address` 单元格、scoped nowrap/ellipsis 样式

- [ ] **Step 1: 用 `.table-scroll` 包裹表格**

将 `v-else` 的 panel 内表格改为：

```vue
    <div v-else class="panel">
      <div class="table-scroll">
        <table class="data-table">
          <!-- 现有 thead / tbody 不变，仅改地点单元格见 Step 2 -->
        </table>
      </div>
      <p v-if="actionError" class="error action-error">{{ actionError }}</p>
    </div>
```

注意：`actionError` 留在 `.table-scroll` 外、仍在 `.panel` 内。

- [ ] **Step 2: 地点单元格加 class 与 title**

将地点 `<td>` 从：

```vue
            <td>{{ row.accidentAddress || '—' }}</td>
```

改为：

```vue
            <td
              class="cell-address"
              :title="row.accidentAddress || undefined"
            >
              {{ row.accidentAddress || '—' }}
            </td>
```

无地址时不设 `title`（`undefined`），避免悬停无意义提示。

- [ ] **Step 3: 增加 scoped 样式**

在该文件 `<style scoped>` 末尾（现有 `.modal-card textarea` 规则之后）追加：

```css
.table-scroll {
  overflow-x: auto;
}

.data-table th,
.data-table td {
  white-space: nowrap;
}

.cell-address {
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

说明：`.cell-address` 的 `nowrap` 与通用 `td` 规则一致；`max-width` + ellipsis 仅作用于地点列。

- [ ] **Step 4: 手动验收**

Run（按项目习惯启动前端，例如）:

```bash
cd frontend && npm run dev
```

在浏览器打开任务管理 `/dispatches`，检查：

1. 短字段（单号、时间、人名、车牌、车型、状态、操作）单行不换行
2. 长「地点」显示省略号；悬停可看到完整地址
3. 窄窗口下表格可横向滚动，页面主体不横向溢出
4. 无工单时空状态行仍正常

Expected: 四点均通过。

- [ ] **Step 5: Commit（仅在用户要求时执行）**

```bash
git add frontend/src/views/dispatch/DispatchList.vue docs/superpowers/specs/2026-09-08-dispatch-list-nowrap-design.md docs/superpowers/plans/2026-09-08-dispatch-list-nowrap.md
git commit -m "$(cat <<'EOF'
fix: keep dispatch list cells on one line with address ellipsis

EOF
)"
```

若用户未要求提交，跳过本步。

---

## Spec coverage self-check

| Spec 要求 | Task |
|-----------|------|
| 短字段 nowrap | Task 1 Step 3 |
| 地点 ellipsis + title | Task 1 Step 2–3 |
| 表格横向滚动 | Task 1 Step 1 + Step 3 |
| 不改 enterprise.css / 筛选 / 详情 | Global Constraints + 单文件改动 |
| 验收标准 | Task 1 Step 4 |

## Placeholder scan

无 TBD / TODO /「类似 Task N」占位。
