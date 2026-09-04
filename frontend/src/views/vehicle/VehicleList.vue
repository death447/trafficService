<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>施救车辆</h1>
        <p class="subtitle">维护拖车、清障车等救援车辆信息与实时状态</p>
      </div>
      <button v-auth="'vehicle:add'" type="button" @click="openCreate">新增车辆</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>
    <div v-else class="panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>车牌号</th>
            <th>类型</th>
            <th>状态</th>
            <th>所属片区</th>
            <th>经度</th>
            <th>纬度</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="vehicle in vehicles" :key="vehicle.id">
            <td>{{ vehicle.plateNo }}</td>
            <td>{{ vehicleTypeLabel(vehicle.vehicleType) }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(vehicle.status)]">
                {{ statusLabel(vehicle.status) }}
              </span>
            </td>
            <td>{{ districtLabel(vehicle.districtId) }}</td>
            <td>{{ vehicle.longitude ?? '—' }}</td>
            <td>{{ vehicle.latitude ?? '—' }}</td>
            <td class="actions">
              <button v-auth="'vehicle:edit'" type="button" @click="openEdit(vehicle)">编辑</button>
              <button v-auth="'vehicle:delete'" type="button" class="danger" @click="onDelete(vehicle)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="formVisible" class="modal" @click.self="formVisible = false">
      <form class="modal-card" @submit.prevent="onSubmit">
        <h2>{{ editingId ? '编辑车辆' : '新增车辆' }}</h2>
        <label>
          车牌号
          <input v-model.trim="form.plateNo" required />
        </label>
        <label>
          车辆类型
          <select v-model="form.vehicleType" required>
            <option value="TOW">拖车</option>
            <option value="CLEARANCE">清障车</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label>
          颜色
          <input v-model.trim="form.color" />
        </label>
        <label>
          装备
          <input v-model.trim="form.equipment" />
        </label>
        <label>
          经度
          <input v-model.trim="form.longitude" type="number" step="any" />
        </label>
        <label>
          纬度
          <input v-model.trim="form.latitude" type="number" step="any" />
        </label>
        <label>
          状态
          <select v-model="form.status" required>
            <option value="IDLE">空闲</option>
            <option value="BUSY">忙碌</option>
            <option value="OFFLINE">离线</option>
          </select>
        </label>
        <label>
          所属片区
          <select v-model="form.districtId">
            <option value="">未绑定</option>
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
import { listVehicles, createVehicle, updateVehicle, deleteVehicle } from '../../api/vehicle'
import { listDistricts } from '../../api/district'

const vehicles = ref([])
const enabledDistricts = ref([])
const allDistricts = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)
const boundDisabledDistrict = ref(null)

const form = reactive({
  plateNo: '',
  vehicleType: 'TOW',
  color: '',
  equipment: '',
  longitude: '',
  latitude: '',
  status: 'IDLE',
  districtId: '',
  remark: ''
})

const vehicleTypeLabels = {
  TOW: '拖车',
  CLEARANCE: '清障车',
  OTHER: '其他'
}

const statusLabels = {
  IDLE: '空闲',
  BUSY: '忙碌',
  OFFLINE: '离线'
}

const districtMap = computed(() => {
  const map = {}
  for (const d of allDistricts.value) map[d.id] = d
  for (const d of enabledDistricts.value) map[d.id] = d
  return map
})

const readonlyDistrict = computed(() => {
  const d = boundDisabledDistrict.value
  if (!d) return null
  if (enabledDistricts.value.some((x) => x.id === d.id)) return null
  return d
})

function vehicleTypeLabel(type) {
  return vehicleTypeLabels[type] || type || '—'
}

function statusLabel(status) {
  return statusLabels[status] || status || '—'
}

function statusBadgeClass(status) {
  if (status === 'IDLE') return 'badge-success'
  if (status === 'BUSY') return 'badge-info'
  return 'badge-muted'
}

function districtLabel(districtId) {
  if (districtId == null) return '—'
  return districtMap.value[districtId]?.name || String(districtId)
}

function resetForm() {
  form.plateNo = ''
  form.vehicleType = 'TOW'
  form.color = ''
  form.equipment = ''
  form.longitude = ''
  form.latitude = ''
  form.status = 'IDLE'
  form.districtId = ''
  form.remark = ''
  formError.value = ''
  boundDisabledDistrict.value = null
}

async function loadDistricts() {
  const [enabledRes, allRes] = await Promise.all([
    listDistricts({ status: 'ENABLED' }),
    listDistricts()
  ])
  enabledDistricts.value = enabledRes.data?.list || []
  allDistricts.value = allRes.data?.list || []
}

async function loadVehicles() {
  loading.value = true
  error.value = ''
  try {
    const res = await listVehicles()
    vehicles.value = res.data?.list || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载车辆失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(vehicle) {
  editingId.value = vehicle.id
  resetForm()
  form.plateNo = vehicle.plateNo || ''
  form.vehicleType = vehicle.vehicleType || 'TOW'
  form.color = vehicle.color || ''
  form.equipment = vehicle.equipment || ''
  form.longitude = vehicle.longitude != null ? String(vehicle.longitude) : ''
  form.latitude = vehicle.latitude != null ? String(vehicle.latitude) : ''
  form.status = vehicle.status || 'IDLE'
  form.remark = vehicle.remark || ''
  form.districtId = vehicle.districtId != null ? String(vehicle.districtId) : ''

  if (vehicle.districtId != null) {
    const current = districtMap.value[vehicle.districtId]
    if (current && current.status !== 'ENABLED') {
      boundDisabledDistrict.value = current
    }
  }
  formVisible.value = true
}

function payload() {
  const data = {
    plateNo: form.plateNo,
    vehicleType: form.vehicleType,
    color: form.color || null,
    equipment: form.equipment || null,
    status: form.status,
    remark: form.remark || null,
    districtId: form.districtId ? Number(form.districtId) : null
  }
  if (form.longitude !== '') {
    data.longitude = Number(form.longitude)
  }
  if (form.latitude !== '') {
    data.latitude = Number(form.latitude)
  }
  return data
}

async function onSubmit() {
  formError.value = ''
  if (
    form.districtId &&
    boundDisabledDistrict.value &&
    Number(form.districtId) === boundDisabledDistrict.value.id
  ) {
    formError.value = '当前片区已停用，请改选启用片区或清空绑定'
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateVehicle(editingId.value, payload())
    } else {
      await createVehicle(payload())
    }
    formVisible.value = false
    await loadVehicles()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function onDelete(vehicle) {
  if (!confirm(`确认删除车辆「${vehicle.plateNo}」？`)) return
  error.value = ''
  try {
    await deleteVehicle(vehicle.id)
    await loadVehicles()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '删除失败'
  }
}

onMounted(async () => {
  try {
    await loadDistricts()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载片区失败'
  }
  await loadVehicles()
})
</script>
