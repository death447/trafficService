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
import { listVehicles, createVehicle, updateVehicle, deleteVehicle } from '../../api/vehicle'

const vehicles = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)

const form = reactive({
  plateNo: '',
  vehicleType: 'TOW',
  color: '',
  equipment: '',
  longitude: '',
  latitude: '',
  status: 'IDLE',
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

function resetForm() {
  form.plateNo = ''
  form.vehicleType = 'TOW'
  form.color = ''
  form.equipment = ''
  form.longitude = ''
  form.latitude = ''
  form.status = 'IDLE'
  form.remark = ''
  formError.value = ''
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
  formVisible.value = true
}

function payload() {
  const data = {
    plateNo: form.plateNo,
    vehicleType: form.vehicleType,
    color: form.color || null,
    equipment: form.equipment || null,
    status: form.status,
    remark: form.remark || null
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

onMounted(() => {
  loadVehicles()
})
</script>
