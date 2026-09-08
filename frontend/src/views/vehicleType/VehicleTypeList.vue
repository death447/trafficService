<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>车型管理</h1>
        <p class="subtitle">维护事故车辆车型字典，支持启用与停用</p>
      </div>
      <button v-auth="'vehicle-type:add'" type="button" @click="openCreate">新增车型</button>
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
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in vehicleTypes" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ row.sortOrder ?? '—' }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(row.status)]">
                {{ statusLabel(row.status) }}
              </span>
            </td>
            <td class="actions">
              <button v-auth="'vehicle-type:edit'" type="button" @click="openEdit(row)">编辑</button>
              <button
                v-auth="'vehicle-type:edit'"
                type="button"
                @click="toggleStatus(row)"
              >
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
          <tr v-if="!vehicleTypes.length">
            <td colspan="4" class="empty-cell">暂无车型</td>
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
        <h2>{{ editingId ? '编辑车型' : '新增车型' }}</h2>
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
import { onMounted, reactive, ref } from 'vue'
import {
  listVehicleTypes,
  createVehicleType,
  updateVehicleType
} from '../../api/vehicleType'
import PaginationBar from '../../components/PaginationBar.vue'

const vehicleTypes = ref([])
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
    const params = { page: pagination.page, size: pagination.size }
    if (filters.status) params.status = filters.status
    const res = await listVehicleTypes(params)
    vehicleTypes.value = res.data?.list || []
    pagination.total = res.data?.total ?? 0
    if (res.data?.page) pagination.page = res.data.page
    if (res.data?.size) pagination.size = res.data.size
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载车型失败'
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
      await updateVehicleType(editingId.value, payload())
    } else {
      await createVehicleType(payload())
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
    await updateVehicleType(row.id, {
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

onMounted(() => {
  loadList()
})
</script>

<style scoped>
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

.filters input,
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
