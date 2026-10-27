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
