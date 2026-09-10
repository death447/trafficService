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
          @keyup.enter="onQuery"
        />
      </label>
      <label>
        扣留编号
        <input
          v-model.trim="filters.detainNo"
          placeholder="DV…"
          @keyup.enter="onQuery"
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
          @keyup.enter="onQuery"
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
            <th>扣押编号</th>
            <th>入场编号</th>
            <th>车牌</th>
            <th>类型</th>
            <th>停车场</th>
            <th>区域</th>
            <th>状态</th>
            <th>入库时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in vehicles" :key="row.id">
            <td>{{ row.detainNo }}</td>
            <td>{{ row.entryNo || '—' }}</td>
            <td>{{ row.plateNo }}</td>
            <td>{{ row.vehicleType || '—' }}</td>
            <td>{{ row.parkingLotName || '—' }}</td>
            <td>{{ row.parkingAreaName || '—' }}</td>
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
            <td colspan="9" class="empty-cell">暂无扣留车辆</td>
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
        <label v-if="!editingId">
          扣押编码
          <input v-model.trim="form.detainNo" required placeholder="强制扣押凭证编码" />
        </label>
        <label v-else>
          扣押编码
          <input :value="form.detainNo" disabled />
        </label>
        <label>
          入场编号
          <input :value="editingId ? form.entryNo : '提交后生成'" disabled />
        </label>
        <label>
          车牌
          <input v-model.trim="form.plateNo" required />
        </label>
        <label>
          厂牌型号
          <input v-model.trim="form.brandModel" />
        </label>
        <label>
          车辆类型
          <input v-model.trim="form.vehicleType" placeholder="如：小型车" />
        </label>
        <label>
          车辆颜色
          <input v-model.trim="form.vehicleColor" />
        </label>
        <label>
          行驶里程
          <input v-model.trim="form.mileage" />
        </label>
        <label>
          重要装备
          <input v-model.trim="form.importantEquipment" />
        </label>
        <label>
          有无钥匙
          <select v-model="form.hasKey">
            <option value="">未填</option>
            <option value="YES">有</option>
            <option value="NO">无</option>
          </select>
        </label>
        <label>
          停车场
          <select v-model="form.parkingLotId" required @change="onFormLotChange">
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
          停放区域
          <select v-model="form.parkingAreaId">
            <option value="">不选</option>
            <option v-for="area in formAreas" :key="area.id" :value="String(area.id)">
              {{ area.name }}{{ area.status === 'DISABLED' ? '（已停用）' : '' }}
            </option>
          </select>
        </label>
        <label>
          车位编号
          <input v-model.trim="form.stallNo" />
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
          施救人员
          <input v-model.trim="form.rescuerName" />
        </label>
        <label>
          施救原因
          <select v-model="form.rescueReason">
            <option value="">未填</option>
            <option value="ACCIDENT">事故</option>
            <option value="ILLEGAL">违法</option>
            <option value="RESCUE">救援</option>
          </select>
        </label>
        <label>
          施救方式
          <input v-model.trim="form.rescueMethod" />
        </label>
        <label>
          施救时间
          <input v-model="form.rescueTime" type="datetime-local" />
        </label>
        <label>
          施救地点
          <input v-model.trim="form.rescueAddress" />
        </label>
        <label>
          备注
          <input v-model.trim="form.remark" />
        </label>
        <div v-if="editingId && formMedias.length" class="media-readonly">
          <p>照片（只读）</p>
          <div class="media-grid">
            <img
              v-for="m in formMedias"
              :key="m.id"
              :src="mediaUrl(m.filePath)"
              :alt="m.bizType"
            />
          </div>
        </div>
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
  clearDetain,
  listDetainMedia
} from '../../api/detain'
import { listParkings, listParkingAreas } from '../../api/parking'
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
  detainNo: '',
  entryNo: '',
  plateNo: '',
  brandModel: '',
  vehicleType: '',
  vehicleColor: '',
  mileage: '',
  importantEquipment: '',
  hasKey: '',
  parkingLotId: '',
  parkingAreaId: '',
  stallNo: '',
  dispatchOrderId: '',
  detainDept: '',
  rescuerName: '',
  rescueReason: '',
  rescueMethod: '',
  rescueTime: '',
  rescueAddress: '',
  remark: ''
})
const formAreas = ref([])
const formMedias = ref([])

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

function mediaUrl(filePath) {
  if (!filePath) return ''
  if (/^https?:\/\//i.test(filePath)) return filePath
  const path = String(filePath).replace(/^\/+/, '')
  return `/uploads/${path}`
}

function toDatetimeLocal(value) {
  if (!value) return ''
  return String(value).replace(' ', 'T').slice(0, 16)
}

async function loadFormAreas(lotId, keepAreaId) {
  formAreas.value = []
  if (!lotId) {
    form.parkingAreaId = ''
    return
  }
  try {
    const res = await listParkingAreas(lotId)
    formAreas.value = res.data || []
    const keep = keepAreaId != null ? String(keepAreaId) : ''
    if (keep && formAreas.value.some((a) => String(a.id) === keep)) {
      form.parkingAreaId = keep
    } else if (!keep) {
      form.parkingAreaId = ''
    }
  } catch {
    formAreas.value = []
  }
}

function onFormLotChange() {
  loadFormAreas(form.parkingLotId, '')
}

function resetForm() {
  form.detainNo = ''
  form.entryNo = ''
  form.plateNo = ''
  form.brandModel = ''
  form.vehicleType = ''
  form.vehicleColor = ''
  form.mileage = ''
  form.importantEquipment = ''
  form.hasKey = ''
  form.parkingLotId = ''
  form.parkingAreaId = ''
  form.stallNo = ''
  form.dispatchOrderId = ''
  form.detainDept = ''
  form.rescuerName = ''
  form.rescueReason = ''
  form.rescueMethod = ''
  form.rescueTime = ''
  form.rescueAddress = ''
  form.remark = ''
  formAreas.value = []
  formMedias.value = []
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
  form.detainNo = row.detainNo || ''
  form.entryNo = row.entryNo || ''
  form.plateNo = row.plateNo || ''
  form.brandModel = row.brandModel || ''
  form.vehicleType = row.vehicleType || ''
  form.vehicleColor = row.vehicleColor || ''
  form.mileage = row.mileage || ''
  form.importantEquipment = row.importantEquipment || ''
  form.hasKey = row.hasKey || ''
  form.parkingLotId = row.parkingLotId != null ? String(row.parkingLotId) : ''
  form.stallNo = row.stallNo || ''
  form.dispatchOrderId = row.dispatchOrderId != null ? String(row.dispatchOrderId) : ''
  form.detainDept = row.detainDept || ''
  form.rescuerName = row.rescuerName || ''
  form.rescueReason = row.rescueReason || ''
  form.rescueMethod = row.rescueMethod || ''
  form.rescueTime = toDatetimeLocal(row.rescueTime)
  form.rescueAddress = row.rescueAddress || ''
  form.remark = row.remark || ''
  formVisible.value = true
  loadFormAreas(form.parkingLotId, row.parkingAreaId)
  listDetainMedia(row.id)
    .then((res) => { formMedias.value = res.data || [] })
    .catch(() => { formMedias.value = [] })
}

function payload() {
  const data = {
    plateNo: form.plateNo,
    vehicleType: form.vehicleType || null,
    brandModel: form.brandModel || null,
    vehicleColor: form.vehicleColor || null,
    mileage: form.mileage || null,
    importantEquipment: form.importantEquipment || null,
    hasKey: form.hasKey || null,
    parkingLotId: Number(form.parkingLotId),
    parkingAreaId: form.parkingAreaId ? Number(form.parkingAreaId) : null,
    stallNo: form.stallNo || null,
    detainDept: form.detainDept || null,
    rescuerName: form.rescuerName || null,
    rescueReason: form.rescueReason || null,
    rescueMethod: form.rescueMethod || null,
    rescueTime: form.rescueTime
      ? (form.rescueTime.length === 16 ? `${form.rescueTime}:00` : form.rescueTime)
      : null,
    rescueAddress: form.rescueAddress || null,
    remark: form.remark || null
  }
  if (!editingId.value) {
    data.detainNo = form.detainNo
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

.modal-card {
  max-height: 88vh;
  overflow: auto;
}

.media-readonly {
  margin: 0.5rem 0 0.75rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.4rem;
}

.media-grid img {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border-strong);
}
</style>
