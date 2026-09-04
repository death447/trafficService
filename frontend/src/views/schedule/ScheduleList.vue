<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>排班管理</h1>
        <p class="subtitle">按日期维护调度员与施救员值班时段</p>
      </div>
      <button v-auth="'schedule:add'" type="button" @click="openCreate">新增排班</button>
    </div>

    <div class="panel filters">
      <label>
        开始日期
        <input v-model="filters.from" type="date" />
      </label>
      <label>
        结束日期
        <input v-model="filters.to" type="date" />
      </label>
      <label>
        角色
        <select v-model="filters.roleType">
          <option value="">全部</option>
          <option value="DISPATCHER">调度员</option>
          <option value="TOW_DRIVER">施救员</option>
        </select>
      </label>
      <label>
        片区
        <select v-model="filters.districtId">
          <option value="">全部</option>
          <option v-for="d in districts" :key="d.id" :value="String(d.id)">
            {{ d.name }}
          </option>
        </select>
      </label>
      <div class="filter-actions">
        <button type="button" @click="loadSchedules">查询</button>
        <button type="button" class="secondary" @click="resetFilters">重置</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>
    <div v-else class="panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>日期</th>
            <th>时段</th>
            <th>人员</th>
            <th>角色</th>
            <th>片区</th>
            <th>车辆</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in schedules" :key="row.id">
            <td>{{ row.dutyDate || '—' }}</td>
            <td>{{ formatRange(row.startTime, row.endTime) }}</td>
            <td>{{ userLabel(row.userId) }}</td>
            <td>{{ roleTypeLabel(row.roleType) }}</td>
            <td>{{ districtLabel(row.districtId) }}</td>
            <td>{{ vehicleLabel(row.vehicleId) }}</td>
            <td class="actions">
              <button v-auth="'schedule:edit'" type="button" @click="openEdit(row)">编辑</button>
              <button
                v-auth="'schedule:delete'"
                type="button"
                class="danger"
                @click="onDelete(row)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="!schedules.length">
            <td colspan="7" class="empty-cell">暂无排班</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="formVisible" class="modal" @click.self="formVisible = false">
      <form class="modal-card" @submit.prevent="onSubmit">
        <h2>{{ editingId ? '编辑排班' : '新增排班' }}</h2>
        <label>
          角色类型
          <select v-model="form.roleType" required @change="onRoleTypeChange">
            <option value="DISPATCHER">调度员</option>
            <option value="TOW_DRIVER">施救员</option>
          </select>
        </label>
        <label>
          人员
          <select v-model="form.userId" required>
            <option value="" disabled>请选择</option>
            <option v-for="u in filteredUsers" :key="u.id" :value="String(u.id)">
              {{ userDisplay(u) }}
            </option>
          </select>
        </label>
        <label>
          开始时间
          <input v-model="form.startTime" type="datetime-local" required />
        </label>
        <label>
          结束时间
          <input v-model="form.endTime" type="datetime-local" required />
        </label>
        <label>
          片区（可选）
          <select v-model="form.districtId">
            <option value="">未指定</option>
            <option
              v-if="readonlyDistrict"
              :value="String(readonlyDistrict.id)"
              disabled
            >
              {{ readonlyDistrict.name }}（已停用）
            </option>
            <option v-for="d in enabledDistricts" :key="d.id" :value="String(d.id)">
              {{ d.name }}
            </option>
          </select>
        </label>
        <label v-if="form.roleType === 'TOW_DRIVER'">
          车辆
          <select v-model="form.vehicleId" required>
            <option value="" disabled>请选择</option>
            <option v-for="v in vehicles" :key="v.id" :value="String(v.id)">
              {{ v.plateNo }}
            </option>
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
  listSchedules,
  createSchedule,
  updateSchedule,
  deleteSchedule
} from '../../api/schedule'
import { getUserList } from '../../api/user'
import { listVehicles } from '../../api/vehicle'
import { listDistricts } from '../../api/district'

const schedules = ref([])
const users = ref([])
const vehicles = ref([])
const districts = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)
const boundDisabledDistrict = ref(null)

function todayStr() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const filters = reactive({
  from: todayStr(),
  to: todayStr(),
  roleType: '',
  districtId: ''
})

const form = reactive({
  roleType: 'DISPATCHER',
  userId: '',
  startTime: '',
  endTime: '',
  districtId: '',
  vehicleId: '',
  remark: ''
})

const roleTypeLabels = {
  DISPATCHER: '调度员',
  TOW_DRIVER: '施救员'
}

const enabledDistricts = computed(() =>
  districts.value.filter((d) => d.status === 'ENABLED')
)

const readonlyDistrict = computed(() => {
  const d = boundDisabledDistrict.value
  if (!d) return null
  if (enabledDistricts.value.some((x) => x.id === d.id)) return null
  return d
})

const filteredUsers = computed(() => {
  const code = form.roleType
  return users.value.filter((u) => {
    if (u.status !== 1) return false
    const roles = u.roles || []
    return roles.some((r) => r.roleCode === code)
  })
})

const userMap = computed(() => {
  const map = {}
  for (const u of users.value) map[u.id] = u
  return map
})

const vehicleMap = computed(() => {
  const map = {}
  for (const v of vehicles.value) map[v.id] = v
  return map
})

const districtMap = computed(() => {
  const map = {}
  for (const d of districts.value) map[d.id] = d
  return map
})

function roleTypeLabel(type) {
  return roleTypeLabels[type] || type || '—'
}

function userDisplay(u) {
  if (!u) return '—'
  return u.realName ? `${u.realName}（${u.username}）` : u.username
}

function userLabel(userId) {
  if (userId == null) return '—'
  return userDisplay(userMap.value[userId]) || String(userId)
}

function vehicleLabel(vehicleId) {
  if (vehicleId == null) return '—'
  return vehicleMap.value[vehicleId]?.plateNo || String(vehicleId)
}

function districtLabel(districtId) {
  if (districtId == null) return '—'
  return districtMap.value[districtId]?.name || String(districtId)
}

function formatRange(start, end) {
  return `${formatTime(start)} ~ ${formatTime(end)}`
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function toDatetimeLocal(value) {
  if (!value) return ''
  return String(value).replace(' ', 'T').slice(0, 16)
}

function toApiDateTime(value) {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

function defaultTimeRange() {
  const day = todayStr()
  return {
    startTime: `${day}T09:00`,
    endTime: `${day}T18:00`
  }
}

function resetForm() {
  const range = defaultTimeRange()
  form.roleType = 'DISPATCHER'
  form.userId = ''
  form.startTime = range.startTime
  form.endTime = range.endTime
  form.districtId = ''
  form.vehicleId = ''
  form.remark = ''
  formError.value = ''
  boundDisabledDistrict.value = null
}

function onRoleTypeChange() {
  form.userId = ''
  form.vehicleId = ''
}

function resetFilters() {
  filters.from = todayStr()
  filters.to = todayStr()
  filters.roleType = ''
  filters.districtId = ''
  loadSchedules()
}

async function loadLookups() {
  const results = await Promise.allSettled([
    getUserList(),
    listVehicles(),
    listDistricts()
  ])
  const [userResult, vehicleResult, districtResult] = results
  if (userResult.status === 'fulfilled') {
    users.value = userResult.value.data?.list || []
  }
  if (vehicleResult.status === 'fulfilled') {
    vehicles.value = vehicleResult.value.data?.list || []
  }
  if (districtResult.status === 'fulfilled') {
    districts.value = districtResult.value.data?.list || []
  }
  const failed = results.filter((r) => r.status === 'rejected')
  if (failed.length === results.length) {
    throw failed[0].reason
  }
  if (failed.length) {
    const first = failed[0].reason
    error.value =
      first?.response?.data?.message || first?.message || '部分基础数据加载失败'
  }
}

async function loadSchedules() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (filters.from) params.from = filters.from
    if (filters.to) params.to = filters.to
    if (filters.roleType) params.roleType = filters.roleType
    if (filters.districtId) params.districtId = Number(filters.districtId)
    const res = await listSchedules(params)
    schedules.value = res.data?.list || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载排班失败'
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
  form.roleType = row.roleType || 'DISPATCHER'
  form.userId = row.userId != null ? String(row.userId) : ''
  form.startTime = toDatetimeLocal(row.startTime)
  form.endTime = toDatetimeLocal(row.endTime)
  form.districtId = row.districtId != null ? String(row.districtId) : ''
  form.vehicleId = row.vehicleId != null ? String(row.vehicleId) : ''
  form.remark = row.remark || ''
  if (row.districtId != null) {
    const current = districtMap.value[row.districtId]
    if (current && current.status !== 'ENABLED') {
      boundDisabledDistrict.value = current
    }
  }
  formVisible.value = true
}

function payload() {
  const data = {
    startTime: toApiDateTime(form.startTime),
    endTime: toApiDateTime(form.endTime),
    userId: Number(form.userId),
    roleType: form.roleType,
    districtId: form.districtId ? Number(form.districtId) : null,
    vehicleId: null,
    remark: form.remark || null
  }
  if (form.roleType === 'TOW_DRIVER') {
    data.vehicleId = form.vehicleId ? Number(form.vehicleId) : null
  }
  return data
}

async function onSubmit() {
  formError.value = ''
  if (form.roleType === 'TOW_DRIVER' && !form.vehicleId) {
    formError.value = '施救员班次必须绑定车辆'
    return
  }
  if (
    form.districtId &&
    boundDisabledDistrict.value &&
    Number(form.districtId) === boundDisabledDistrict.value.id
  ) {
    formError.value = '当前片区已停用，请改选启用片区或清空'
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateSchedule(editingId.value, payload())
    } else {
      await createSchedule(payload())
    }
    formVisible.value = false
    await loadSchedules()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  const who = userLabel(row.userId)
  if (!confirm(`确认删除「${who}」在 ${formatTime(row.startTime)} 的排班？`)) return
  error.value = ''
  try {
    await deleteSchedule(row.id)
    await loadSchedules()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '删除失败'
  }
}

onMounted(async () => {
  try {
    await loadLookups()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载基础数据失败'
  }
  await loadSchedules()
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
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  min-width: 160px;
}

.filter-actions {
  display: flex;
  gap: 0.4rem;
}

.empty-cell {
  text-align: center;
  color: var(--text-secondary);
  padding: 1.5rem 1rem !important;
}

.loading-text {
  color: var(--text-secondary);
  font-size: 0.875rem;
}
</style>
