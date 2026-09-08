<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>停车场管理</h1>
        <p class="subtitle">维护停车场编码、联系人与启用状态</p>
      </div>
      <button v-auth="'parking:add'" type="button" @click="openCreate">新增停车场</button>
    </div>

    <div class="panel filters">
      <label>
        关键词
        <input
          v-model.trim="filters.keyword"
          placeholder="名称 / 编码"
          @keyup.enter="loadList"
        />
      </label>
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
            <th>编码</th>
            <th>地址</th>
            <th>联系人</th>
            <th>联系电话</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="lot in parkings" :key="lot.id">
            <td>{{ lot.name }}</td>
            <td>{{ lot.code }}</td>
            <td>{{ lot.address || '—' }}</td>
            <td>{{ lot.contactName || '—' }}</td>
            <td>{{ lot.contactPhone || '—' }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(lot.status)]">
                {{ statusLabel(lot.status) }}
              </span>
            </td>
            <td class="actions">
              <button v-auth="'parking:edit'" type="button" @click="openEdit(lot)">编辑</button>
              <button
                v-auth="'parking:delete'"
                type="button"
                class="danger"
                @click="onDelete(lot)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="!parkings.length">
            <td colspan="7" class="empty-cell">暂无停车场</td>
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
        <h2>{{ editingId ? '编辑停车场' : '新增停车场' }}</h2>
        <label>
          名称
          <input v-model.trim="form.name" required />
        </label>
        <label>
          编码
          <input v-model.trim="form.code" required :disabled="!!editingId" />
        </label>
        <label>
          地址
          <input v-model.trim="form.address" />
        </label>
        <label>
          联系人
          <input v-model.trim="form.contactName" />
        </label>
        <label>
          联系电话
          <input v-model.trim="form.contactPhone" />
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
  listParkings,
  createParking,
  updateParking,
  deleteParking
} from '../../api/parking'
import PaginationBar from '../../components/PaginationBar.vue'

const parkings = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)

const filters = reactive({
  keyword: '',
  status: ''
})

const form = reactive({
  name: '',
  code: '',
  address: '',
  contactName: '',
  contactPhone: '',
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
  form.code = ''
  form.address = ''
  form.contactName = ''
  form.contactPhone = ''
  form.status = 'ENABLED'
  form.remark = ''
  formError.value = ''
}

function resetFilters() {
  filters.keyword = ''
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
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.status) params.status = filters.status
    const res = await listParkings(params)
    parkings.value = res.data?.list || []
    pagination.total = res.data?.total ?? 0
    if (res.data?.page) pagination.page = res.data.page
    if (res.data?.size) pagination.size = res.data.size
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载停车场失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(lot) {
  editingId.value = lot.id
  resetForm()
  form.name = lot.name || ''
  form.code = lot.code || ''
  form.address = lot.address || ''
  form.contactName = lot.contactName || ''
  form.contactPhone = lot.contactPhone || ''
  form.status = lot.status || 'ENABLED'
  form.remark = lot.remark || ''
  formVisible.value = true
}

function payload() {
  return {
    name: form.name,
    code: form.code,
    address: form.address || null,
    contactName: form.contactName || null,
    contactPhone: form.contactPhone || null,
    status: form.status,
    remark: form.remark || null
  }
}

async function onSubmit() {
  formError.value = ''
  saving.value = true
  try {
    if (editingId.value) {
      await updateParking(editingId.value, payload())
    } else {
      await createParking(payload())
    }
    formVisible.value = false
    await loadList()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function onDelete(lot) {
  if (!confirm(`确认删除停车场「${lot.name}」？`)) return
  error.value = ''
  try {
    await deleteParking(lot.id)
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
