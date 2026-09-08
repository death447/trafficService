<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>扣留车辆</h1>
        <p class="subtitle">入库、出库、清理与吊牌打印</p>
      </div>
      <button v-auth="'detain:add'" type="button" @click="openCreate">入库</button>
    </div>

    <div class="panel filters">
      <label>
        车牌
        <input
          v-model.trim="filters.plateNo"
          placeholder="车牌号"
          @keyup.enter="loadList"
        />
      </label>
      <label>
        扣留编号
        <input
          v-model.trim="filters.detainNo"
          placeholder="DV…"
          @keyup.enter="loadList"
        />
      </label>
      <label>
        状态
        <select v-model="filters.status">
          <option value="">全部</option>
          <option value="IN_YARD">在库</option>
          <option value="OUT">已出库</option>
          <option value="CLEARED">已清理</option>
        </select>
      </label>
      <label>
        停车场
        <select v-model="filters.parkingLotId">
          <option value="">全部</option>
          <option v-for="lot in parkingLots" :key="lot.id" :value="String(lot.id)">
            {{ lot.name }}
          </option>
        </select>
      </label>
      <label>
        扣留部门
        <input
          v-model.trim="filters.detainDept"
          placeholder="部门关键词"
          @keyup.enter="loadList"
        />
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
            <th>扣留编号</th>
            <th>车牌</th>
            <th>类型</th>
            <th>停车场</th>
            <th>扣留部门</th>
            <th>状态</th>
            <th>入库时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in vehicles" :key="row.id">
            <td>{{ row.detainNo }}</td>
            <td>{{ row.plateNo }}</td>
            <td>{{ row.vehicleType || '—' }}</td>
            <td>{{ row.parkingLotName || '—' }}</td>
            <td>{{ row.detainDept || '—' }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(row.status)]">
                {{ statusLabel(row.status) }}
              </span>
            </td>
            <td>{{ formatTime(row.inTime) }}</td>
            <td class="actions">
              <button
                v-if="row.status === 'IN_YARD'"
                v-auth="'detain:edit'"
                type="button"
                @click="openEdit(row)"
              >
                编辑
              </button>
              <button
                v-if="row.status === 'IN_YARD'"
                v-auth="'detain:out'"
                type="button"
                class="secondary"
                @click="onCheckOut(row)"
              >
                出库
              </button>
              <button
                v-if="row.status === 'OUT'"
                v-auth="'detain:clear'"
                type="button"
                class="secondary"
                @click="onClear(row)"
              >
                清理
              </button>
              <router-link
                v-auth="'detain:print'"
                :to="`/detained-vehicles/${row.id}/hangtag`"
                class="link-btn"
                target="_blank"
              >
                打印吊牌
              </router-link>
            </td>
          </tr>
          <tr v-if="!vehicles.length">
            <td colspan="8" class="empty-cell">暂无扣留车辆</td>
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
        <h2>{{ editingId ? '编辑扣留车辆' : '车辆入库' }}</h2>
        <label>
          车牌
          <input v-model.trim="form.plateNo" required />
        </label>
        <label>
          车辆类型
          <input v-model.trim="form.vehicleType" placeholder="如：小型车" />
        </label>
        <label>
          停车场
          <select v-model="form.parkingLotId" required>
            <option value="" disabled>请选择启用停车场</option>
            <option
              v-for="lot in formParkingOptions"
              :key="lot.id"
              :value="String(lot.id)"
            >
              {{ lot.name }}{{ lot.status === 'DISABLED' ? '（已停用）' : '' }}
            </option>
          </select>
        </label>
        <label>
          关联工单 ID
          <input v-model.trim="form.dispatchOrderId" type="number" min="1" placeholder="可选" />
        </label>
        <label>
          扣留部门
          <input v-model.trim="form.detainDept" />
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
  listDetains,
  checkInDetain,
  updateDetain,
  checkOutDetain,
  clearDetain
} from '../../api/detain'
import { listParkings } from '../../api/parking'
import PaginationBar from '../../components/PaginationBar.vue'

const vehicles = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const parkingLots = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)

const filters = reactive({
  plateNo: '',
  detainNo: '',
  status: '',
  parkingLotId: '',
  detainDept: ''
})

const form = reactive({
  plateNo: '',
  vehicleType: '',
  parkingLotId: '',
  dispatchOrderId: '',
  detainDept: '',
  remark: ''
})

const statusLabels = {
  IN_YARD: '在库',
  OUT: '已出库',
  CLEARED: '已清理'
}

const enabledParkings = computed(() =>
  parkingLots.value.filter((lot) => lot.status === 'ENABLED')
)

/** Edit: include current lot even if DISABLED; create: ENABLED only. Reject other disabled lots. */
const formParkingOptions = computed(() => {
  const enabled = enabledParkings.value
  if (!editingId.value || !form.parkingLotId) {
    return enabled
  }
  const currentId = String(form.parkingLotId)
  if (enabled.some((lot) => String(lot.id) === currentId)) {
    return enabled
  }
  const current = parkingLots.value.find((lot) => String(lot.id) === currentId)
  return current ? [current, ...enabled] : enabled
})

function statusLabel(status) {
  return statusLabels[status] || status || '—'
}

function statusBadgeClass(status) {
  if (status === 'IN_YARD') return 'badge-success'
  if (status === 'OUT') return 'badge-info'
  return 'badge-muted'
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

function resetForm() {
  form.plateNo = ''
  form.vehicleType = ''
  form.parkingLotId = ''
  form.dispatchOrderId = ''
  form.detainDept = ''
  form.remark = ''
  formError.value = ''
}

function resetFilters() {
  filters.plateNo = ''
  filters.detainNo = ''
  filters.status = ''
  filters.parkingLotId = ''
  filters.detainDept = ''
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

async function loadParkings() {
  try {
    const res = await listParkings({ page: 1, size: 100 })
    parkingLots.value = res.data?.list || []
  } catch {
    parkingLots.value = []
  }
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: pagination.page, size: pagination.size }
    if (filters.plateNo) params.plateNo = filters.plateNo
    if (filters.detainNo) params.detainNo = filters.detainNo
    if (filters.status) params.status = filters.status
    if (filters.parkingLotId) params.parkingLotId = Number(filters.parkingLotId)
    if (filters.detainDept) params.detainDept = filters.detainDept
    const res = await listDetains(params)
    vehicles.value = res.data?.list || []
    pagination.total = res.data?.total ?? 0
    if (res.data?.page) pagination.page = res.data.page
    if (res.data?.size) pagination.size = res.data.size
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载扣留车辆失败'
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
  form.plateNo = row.plateNo || ''
  form.vehicleType = row.vehicleType || ''
  form.parkingLotId = row.parkingLotId != null ? String(row.parkingLotId) : ''
  form.dispatchOrderId = row.dispatchOrderId != null ? String(row.dispatchOrderId) : ''
  form.detainDept = row.detainDept || ''
  form.remark = row.remark || ''
  formVisible.value = true
}

function payload() {
  const data = {
    plateNo: form.plateNo,
    vehicleType: form.vehicleType || null,
    parkingLotId: Number(form.parkingLotId),
    detainDept: form.detainDept || null,
    remark: form.remark || null
  }
  if (form.dispatchOrderId) {
    data.dispatchOrderId = Number(form.dispatchOrderId)
  } else {
    data.dispatchOrderId = null
  }
  return data
}

async function onSubmit() {
  formError.value = ''
  saving.value = true
  try {
    if (editingId.value) {
      await updateDetain(editingId.value, payload())
    } else {
      await checkInDetain(payload())
    }
    formVisible.value = false
    await loadList()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function onCheckOut(row) {
  if (!confirm(`确认将「${row.plateNo}」出库？`)) return
  error.value = ''
  try {
    await checkOutDetain(row.id)
    await loadList()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '出库失败'
  }
}

async function onClear(row) {
  if (!confirm(`确认清理「${row.plateNo}」？清理后不可再编辑。`)) return
  error.value = ''
  try {
    await clearDetain(row.id)
    await loadList()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '清理失败'
  }
}

onMounted(async () => {
  await loadParkings()
  await loadList()
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

.link-btn {
  display: inline-block;
  padding: 0.28rem 0.55rem;
  font-size: 0.8rem;
  color: var(--accent-hover);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: #fff;
  text-decoration: none;
}

.link-btn:hover {
  border-color: #9cc7e6;
  color: var(--accent-hover);
}
</style>
