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
            <th>施救员</th>
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
            <td>{{ driverLabel(vehicle) }}</td>
            <td>{{ districtLabel(vehicle.districtId) }}</td>
            <td>{{ vehicle.longitude ?? '—' }}</td>
            <td>{{ vehicle.latitude ?? '—' }}</td>
            <td class="actions">
              <button type="button" class="secondary" @click="openQr(vehicle)">二维码</button>
              <button v-auth="'vehicle:edit'" type="button" @click="openEdit(vehicle)">编辑</button>
              <button v-auth="'vehicle:delete'" type="button" class="danger" @click="onDelete(vehicle)">删除</button>
            </td>
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

    <div v-if="qrVisible" class="modal qr-modal" @click.self="qrVisible = false">
      <div class="modal-card qr-card">
        <h2>车辆二维码</h2>
        <p v-if="qrVehicle" class="qr-plate">{{ qrVehicle.plateNo }}</p>
        <p v-if="qrError" class="error">{{ qrError }}</p>
        <div v-else-if="qrDataUrl" class="qr-print-area">
          <img :src="qrDataUrl" alt="车辆二维码" class="qr-image" />
          <p class="qr-payload">RV:{{ qrVehicle?.id }}</p>
        </div>
        <p v-else class="loading-text">生成中…</p>
        <div class="modal-actions no-print">
          <button type="button" class="secondary" @click="qrVisible = false">关闭</button>
          <button type="button" :disabled="!qrDataUrl" @click="onPrintQr">打印</button>
        </div>
      </div>
    </div>

    <div v-if="formVisible" class="modal vehicle-form-modal" @click.self="closeForm">
      <form class="modal-card vehicle-form-card" @submit.prevent="onSubmit">
        <div class="vehicle-form-layout">
          <div class="vehicle-form-fields">
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
              施救员
              <select v-model="form.driverUserId">
                <option value="">未绑定（待扫码上线）</option>
                <option v-for="u in rescuerOptions" :key="u.id" :value="String(u.id)">
                  {{ userDisplay(u) }}
                </option>
              </select>
            </label>
            <label>
              备注
              <input v-model.trim="form.remark" />
            </label>
            <div class="coord-readonly">
              <span class="coord-label">位置（地图点选）</span>
              <p class="coord-values">
                <template v-if="hasPickedCoords">
                  经度 {{ form.longitude }} · 纬度 {{ form.latitude }}
                </template>
                <template v-else>未选点</template>
              </p>
              <button
                v-if="hasPickedCoords"
                type="button"
                class="secondary small"
                @click="clearLocation"
              >
                清除位置
              </button>
            </div>
            <p v-if="formError" class="error">{{ formError }}</p>
            <div class="modal-actions">
              <button type="button" class="secondary" @click="closeForm">取消</button>
              <button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
            </div>
          </div>
          <div class="vehicle-form-map">
            <p v-if="!amapReady" class="map-hint">
              未配置 VITE_AMAP_KEY，无法用地图选点；请配置 Key 后刷新。
            </p>
            <p v-else-if="mapError" class="error">{{ mapError }}</p>
            <p v-else class="map-hint">点击地图选点，可拖拽标记微调；灰色为启用片区范围。</p>
            <div v-if="amapReady" ref="mapEl" class="map-box" />
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import QRCode from 'qrcode'
import { listVehicles, createVehicle, updateVehicle, deleteVehicle } from '../../api/vehicle'
import { listDistricts } from '../../api/district'
import { getUserList } from '../../api/user'
import {
  clearDistrictOverlays,
  createPickerMap,
  drawDistrictOverlays,
  hasAmapKey
} from '../../utils/amap'
import { loadEnabledDistrictOverlayItems } from '../../utils/districtOverlay'
import PaginationBar from '../../components/PaginationBar.vue'

const vehicles = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const enabledDistricts = ref([])
const allDistricts = ref([])
const users = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const qrVisible = ref(false)
const qrVehicle = ref(null)
const qrDataUrl = ref('')
const qrError = ref('')
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)
const boundDisabledDistrict = ref(null)
const mapEl = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
let mapInstance = null
let districtHandles = null

const hasPickedCoords = computed(
  () => form.longitude !== '' && form.latitude !== ''
)

const form = reactive({
  plateNo: '',
  vehicleType: 'TOW',
  color: '',
  equipment: '',
  longitude: '',
  latitude: '',
  status: 'OFFLINE',
  districtId: '',
  driverUserId: '',
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

const rescuerOptions = computed(() =>
  users.value.filter((u) => {
    if (u.status !== 1) return false
    const roles = u.roles || []
    return roles.some((r) => r.roleCode === 'TOW_DRIVER')
  })
)

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

function driverLabel(vehicle) {
  if (!vehicle) return '—'
  if (vehicle.driverName) return vehicle.driverName
  if (vehicle.driverUserId == null) return '—'
  return String(vehicle.driverUserId)
}

function userDisplay(u) {
  if (!u) return '—'
  return u.realName ? `${u.realName}（${u.username}）` : u.username
}

function resetForm() {
  form.plateNo = ''
  form.vehicleType = 'TOW'
  form.color = ''
  form.equipment = ''
  form.longitude = ''
  form.latitude = ''
  form.status = 'OFFLINE'
  form.districtId = ''
  form.driverUserId = ''
  form.remark = ''
  formError.value = ''
  boundDisabledDistrict.value = null
}

async function loadDistricts() {
  const [enabledRes, allRes] = await Promise.all([
    listDistricts({ status: 'ENABLED', page: 1, size: 100 }),
    listDistricts({ page: 1, size: 100 })
  ])
  enabledDistricts.value = enabledRes.data?.list || []
  allDistricts.value = allRes.data?.list || []
}

async function loadUsers() {
  try {
    const res = await getUserList({ page: 1, size: 100 })
    users.value = res.data?.list || []
  } catch (_) {
    users.value = []
  }
}

async function loadVehicles() {
  loading.value = true
  error.value = ''
  try {
    const res = await listVehicles({ page: pagination.page, size: pagination.size })
    vehicles.value = res.data?.list || []
    pagination.total = res.data?.total ?? 0
    if (res.data?.page) pagination.page = res.data.page
    if (res.data?.size) pagination.size = res.data.size
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载车辆失败'
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  pagination.page = p
  loadVehicles()
}

function onSizeChange(s) {
  pagination.size = s
  pagination.page = 1
  loadVehicles()
}

function destroyMap() {
  if (mapInstance?.map) {
    clearDistrictOverlays(mapInstance.map, districtHandles)
  }
  districtHandles = null
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
}

async function initMap() {
  destroyMap()
  mapError.value = ''
  if (!amapReady || !mapEl.value) return
  try {
    const lng = form.longitude !== '' ? form.longitude : undefined
    const lat = form.latitude !== '' ? form.latitude : undefined
    mapInstance = await createPickerMap(mapEl.value, {
      lng,
      lat,
      onPicked({ lng: x, lat: y }) {
        form.longitude = String(x)
        form.latitude = String(y)
      }
    })
    const districtItems = await loadEnabledDistrictOverlayItems()
    districtHandles = drawDistrictOverlays(mapInstance.map, districtItems)
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

function clearLocation() {
  form.longitude = ''
  form.latitude = ''
  mapInstance?.clearMarker?.()
}

function closeForm() {
  formVisible.value = false
  destroyMap()
}

async function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
  await nextTick()
  await initMap()
}

async function openEdit(vehicle) {
  editingId.value = vehicle.id
  resetForm()
  form.plateNo = vehicle.plateNo || ''
  form.vehicleType = vehicle.vehicleType || 'TOW'
  form.color = vehicle.color || ''
  form.equipment = vehicle.equipment || ''
  form.longitude = vehicle.longitude != null ? String(vehicle.longitude) : ''
  form.latitude = vehicle.latitude != null ? String(vehicle.latitude) : ''
  form.status = vehicle.status || 'OFFLINE'
  form.remark = vehicle.remark || ''
  form.districtId = vehicle.districtId != null ? String(vehicle.districtId) : ''
  form.driverUserId = vehicle.driverUserId != null ? String(vehicle.driverUserId) : ''

  if (vehicle.districtId != null) {
    const current = districtMap.value[vehicle.districtId]
    if (current && current.status !== 'ENABLED') {
      boundDisabledDistrict.value = current
    }
  }
  formVisible.value = true
  await nextTick()
  await initMap()
}

function payload() {
  return {
    plateNo: form.plateNo,
    vehicleType: form.vehicleType,
    color: form.color || null,
    equipment: form.equipment || null,
    status: form.status,
    remark: form.remark || null,
    districtId: form.districtId ? Number(form.districtId) : null,
    driverUserId: form.driverUserId ? Number(form.driverUserId) : null,
    longitude: form.longitude !== '' ? Number(form.longitude) : null,
    latitude: form.latitude !== '' ? Number(form.latitude) : null
  }
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
    closeForm()
    await loadVehicles()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function openQr(vehicle) {
  qrVehicle.value = vehicle
  qrDataUrl.value = ''
  qrError.value = ''
  qrVisible.value = true
  try {
    qrDataUrl.value = await QRCode.toDataURL(`RV:${vehicle.id}`, {
      width: 240,
      margin: 1,
      errorCorrectionLevel: 'M'
    })
  } catch (e) {
    qrError.value = e.message || '生成二维码失败'
  }
}

function onPrintQr() {
  window.print()
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
    await Promise.all([loadDistricts(), loadUsers()])
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载基础数据失败'
  }
  await loadVehicles()
})

onBeforeUnmount(() => {
  destroyMap()
})
</script>

<style scoped>
.vehicle-form-modal {
  padding: 1.25rem 1rem;
  align-items: stretch;
}

.vehicle-form-card {
  width: min(1100px, calc(100vw - 1.5rem));
  max-width: none;
  max-height: calc(100vh - 2.5rem);
  overflow: auto;
  padding: 1rem 1.15rem;
}

.vehicle-form-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 1rem 1.25rem;
  align-items: start;
}

.vehicle-form-fields {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  min-width: 0;
}

.vehicle-form-fields h2 {
  margin: 0;
}

.coord-readonly {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.55rem 0.65rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #f8fafc;
}

.coord-label {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.coord-values {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text);
  word-break: break-all;
}

button.small {
  align-self: flex-start;
  padding: 0.3rem 0.65rem;
  font-size: 0.8rem;
}

.vehicle-form-map {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.map-hint {
  margin: 0;
  font-size: 0.78rem;
  color: var(--text-secondary);
}

.map-box {
  height: min(62vh, 560px);
  min-height: 360px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-muted);
}

@media (max-width: 860px) {
  .vehicle-form-layout {
    grid-template-columns: 1fr;
  }

  .map-box {
    height: min(45vh, 420px);
    min-height: 280px;
  }
}

.qr-card {
  text-align: center;
  max-width: 320px;
}

.qr-plate {
  margin: 0 0 0.75rem;
  font-size: 1rem;
  font-weight: 600;
}

.qr-print-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.qr-image {
  width: 240px;
  height: 240px;
}

.qr-payload {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-secondary);
  word-break: break-all;
}

@media print {
  :global(.page-header),
  :global(.panel),
  :global(.modal:not(.qr-modal)),
  .no-print {
    display: none !important;
  }

  .qr-modal {
    position: static;
    background: transparent;
    padding: 0;
  }

  .qr-card {
    box-shadow: none;
    border: none;
    max-width: none;
  }
}
</style>
