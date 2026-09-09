<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>新建工单</h1>
        <p class="subtitle">填写事故信息并提交派单申请</p>
      </div>
      <button type="button" class="secondary" @click="goBack">返回列表</button>
    </div>

    <p v-if="!amapReady" class="hint">未配置 VITE_AMAP_KEY，无法用地图选点，请先配置高德 Key。</p>
    <p v-if="mapError" class="error">{{ mapError }}</p>
    <p v-if="lookupError" class="error">{{ lookupError }}</p>

    <form class="panel form-panel" @submit.prevent="onSubmit">
      <div class="create-layout">
        <div class="form-col">
          <label>
            车牌号码
            <input v-model.trim="form.plateNo" placeholder="选填" maxlength="20" />
          </label>
          <label>
            车型
            <select v-model="form.vehicleTypeId">
              <option value="">不选择</option>
              <option v-for="t in vehicleTypes" :key="t.id" :value="String(t.id)">
                {{ t.name }}
              </option>
            </select>
          </label>
          <label>
            事故地点
            <input
              ref="searchInput"
              v-model.trim="form.accidentAddress"
              required
              placeholder="输入地点后回车定位，或从下拉选点 / 点击地图"
              @keydown.enter.prevent="onAddressEnter"
            />
          </label>
          <label>
            调度员
            <input type="text" :value="currentDispatcherLabel" readonly class="readonly-input" />
          </label>
          <label>
            救援车辆
            <select v-model="form.vehicleId">
              <option value="">暂不指定</option>
              <option v-for="v in idleVehicles" :key="v.id" :value="String(v.id)">
                {{ vehicleDisplay(v) }}
              </option>
            </select>
            <span v-if="nearbyHint" class="field-hint">{{ nearbyHint }}</span>
          </label>
          <label>
            施救员
            <input type="text" :value="boundRescuerLabel" readonly class="readonly-input" />
          </label>
          <label>
            施救原因
            <textarea v-model.trim="form.rescueReason" rows="4" required placeholder="简要描述事故原因" />
          </label>

          <p v-if="formError" class="error">{{ formError }}</p>
          <div class="form-actions">
            <button type="button" class="secondary" @click="goBack">取消</button>
            <button type="submit" :disabled="saving">{{ saving ? '提交中…' : '提交工单' }}</button>
          </div>
        </div>

        <div class="map-col">
          <div v-if="amapReady" class="map-wrap">
            <p class="map-hint">
              输入地址回车或地图选点定位事故；定位后地图会标注附近空闲车辆，点击标记可选车
            </p>
            <div ref="mapEl" class="map-box" />
          </div>
          <p v-else class="map-placeholder">未配置 VITE_AMAP_KEY，地图不可用；仍可填写表单，但需配置 Key 后才能选点定位。</p>
        </div>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createDispatch } from '../../api/dispatch'
import { listVehicles, nearbyVehicles } from '../../api/vehicle'
import { listEnabledVehicleTypes } from '../../api/vehicleType'
import { useUserStore } from '../../stores/user'
import { createPickerMap, createVehicleMapIcon, hasAmapKey } from '../../utils/amap'

const router = useRouter()
const userStore = useUserStore()
const mapEl = ref(null)
const searchInput = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
const lookupError = ref('')
const formError = ref('')
const nearbyHint = ref('')
const saving = ref(false)
const vehicles = ref([])
const vehicleTypes = ref([])
const nearbyFresh = ref([])
const nearbyStale = ref([])
let mapInstance = null
let vehicleMarkers = []

const form = reactive({
  rescueReason: '',
  accidentAddress: '',
  longitude: '',
  latitude: '',
  rescuerId: '',
  vehicleId: '',
  plateNo: '',
  vehicleTypeId: ''
})

const currentDispatcherLabel = computed(() => {
  const name = userStore.username || ''
  return name ? `${name}（当前登录）` : '当前登录账号'
})

const distanceByVehicleId = computed(() => {
  const map = new Map()
  for (const item of [...nearbyFresh.value, ...nearbyStale.value]) {
    const id = item.vehicle?.id
    if (id != null) map.set(id, item.distanceMeters)
  }
  return map
})

/** IDLE vehicles: nearby distance order first, then the rest */
const idleVehicles = computed(() => {
  const byId = new Map(
    vehicles.value.filter((v) => v.status === 'IDLE').map((v) => [v.id, v])
  )
  const ordered = []
  const seen = new Set()
  for (const item of [...nearbyFresh.value, ...nearbyStale.value]) {
    const id = item.vehicle?.id
    if (id == null || seen.has(id) || !byId.has(id)) continue
    ordered.push(byId.get(id))
    seen.add(id)
  }
  for (const v of byId.values()) {
    if (!seen.has(v.id)) ordered.push(v)
  }
  return ordered
})

const selectedVehicle = computed(() => {
  if (!form.vehicleId) return null
  return idleVehicles.value.find((v) => String(v.id) === form.vehicleId) || null
})

const boundRescuerLabel = computed(() => {
  const v = selectedVehicle.value
  if (!form.vehicleId) return '请先选择救援车辆'
  if (!v) return '—'
  if (v.driverUserId == null) return '该车辆未绑定施救员'
  if (v.driverName) return v.driverName
  return `用户#${v.driverUserId}`
})

watch(
  () => form.vehicleId,
  (id) => {
    if (!id) {
      form.rescuerId = ''
      return
    }
    const v = idleVehicles.value.find((item) => String(item.id) === id)
    form.rescuerId = v?.driverUserId != null ? String(v.driverUserId) : ''
  }
)

function formatDistance(meters) {
  if (meters == null || Number.isNaN(Number(meters))) return '距离未知'
  const m = Number(meters)
  if (m < 1000) return `${Math.round(m)} m`
  return `${(m / 1000).toFixed(1)} km`
}

function vehicleDisplay(v) {
  const driver =
    v.driverName || (v.driverUserId != null ? `施救员#${v.driverUserId}` : '未绑定施救员')
  const dist = distanceByVehicleId.value.get(v.id)
  const distPart =
    form.longitude !== '' && form.latitude !== '' ? ` · ${formatDistance(dist)}` : ''
  return `${v.plateNo}（空闲 · ${driver}${distPart}）`
}

function clearVehicleMarkers() {
  vehicleMarkers.forEach((m) => {
    if (m && typeof m.setMap === 'function') m.setMap(null)
  })
  vehicleMarkers = []
}

function syncVehicleMarkers() {
  clearVehicleMarkers()
  const map = mapInstance?.map
  if (!map || !window.AMap) return
  const AMap = window.AMap
  const icon = createVehicleMapIcon(AMap)
  for (const item of [...nearbyFresh.value, ...nearbyStale.value]) {
    const v = item.vehicle
    if (v?.longitude == null || v?.latitude == null) continue
    const marker = new AMap.Marker({
      position: [Number(v.longitude), Number(v.latitude)],
      map,
      icon,
      offset: new AMap.Pixel(-22, -18),
      title: v.plateNo || '',
      label: {
        content: `${v.plateNo || ''} ${formatDistance(item.distanceMeters)}`,
        direction: 'top',
        offset: new AMap.Pixel(0, -4)
      }
    })
    marker.on('click', () => {
      form.vehicleId = String(v.id)
    })
    vehicleMarkers.push(marker)
  }
}

async function refreshNearby() {
  nearbyHint.value = ''
  const lng = Number(form.longitude)
  const lat = Number(form.latitude)
  if (
    form.longitude === '' ||
    form.latitude === '' ||
    Number.isNaN(lng) ||
    Number.isNaN(lat)
  ) {
    nearbyFresh.value = []
    nearbyStale.value = []
    clearVehicleMarkers()
    return
  }
  try {
    const res = await nearbyVehicles({ lng, lat, limit: 50 })
    nearbyFresh.value = res.data?.vehicles || []
    nearbyStale.value = res.data?.staleVehicles || []
    const marked =
      [...nearbyFresh.value, ...nearbyStale.value].filter(
        (i) => i.vehicle?.longitude != null && i.vehicle?.latitude != null
      ).length
    if (marked === 0) {
      nearbyHint.value = '附近暂无可标注坐标的空闲车辆（需车辆有位置或施救员上报 GPS）'
    } else if (!nearbyFresh.value.length) {
      nearbyHint.value = '暂无实时定位车辆，已按历史坐标标注可派车辆'
    }
    syncVehicleMarkers()
  } catch (e) {
    nearbyFresh.value = []
    nearbyStale.value = []
    clearVehicleMarkers()
    nearbyHint.value = e?.response?.data?.message || e?.message || '加载附近车辆失败'
  }
}

function toNullableId(value) {
  if (value === '' || value == null) return null
  const n = Number(value)
  return Number.isNaN(n) ? null : n
}

function goBack() {
  router.push('/dispatches')
}

async function loadLookups() {
  lookupError.value = ''
  try {
    const res = await listVehicles({ status: 'IDLE', page: 1, size: 100 })
    vehicles.value = res.data?.list || []
  } catch (e) {
    vehicles.value = []
    lookupError.value =
      e?.response?.data?.message || e?.message || '加载空闲车辆失败'
  }
}

async function initMap() {
  if (!amapReady || !mapEl.value) return
  try {
    mapInstance = await createPickerMap(mapEl.value, {
      searchInput: searchInput.value,
      onPicked({ lng, lat, address }) {
        form.longitude = String(lng)
        form.latitude = String(lat)
        if (address) form.accidentAddress = address
        refreshNearby()
      }
    })
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

async function onAddressEnter() {
  mapError.value = ''
  if (!mapInstance?.geocodeAddress) {
    mapError.value = amapReady ? '地图尚未就绪' : '未配置地图 Key，无法定位'
    return
  }
  try {
    await mapInstance.geocodeAddress(form.accidentAddress)
    await refreshNearby()
  } catch (e) {
    mapError.value = e.message || '地点解析失败'
  }
}

async function onSubmit() {
  formError.value = ''
  const lng = Number(form.longitude)
  const lat = Number(form.latitude)
  if (
    form.longitude === '' ||
    form.latitude === '' ||
    Number.isNaN(lng) ||
    Number.isNaN(lat)
  ) {
    formError.value = '请通过地图选点或输入事故地点后回车定位'
    return
  }
  if (userStore.userId == null) {
    formError.value = '未获取到当前登录用户，请重新登录'
    return
  }
  saving.value = true
  try {
    const res = await createDispatch({
      rescueReason: form.rescueReason,
      accidentAddress: form.accidentAddress,
      longitude: lng,
      latitude: lat,
      dispatcherId: userStore.userId,
      rescuerId: toNullableId(form.rescuerId),
      vehicleId: toNullableId(form.vehicleId),
      plateNo: form.plateNo || null,
      vehicleTypeId: form.vehicleTypeId ? Number(form.vehicleTypeId) : null
    })
    const id = res.data?.id
    if (id != null) {
      router.push(`/dispatches/${id}`)
    } else {
      router.push('/dispatches')
    }
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '创建工单失败'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadLookups()
  listEnabledVehicleTypes()
    .then((res) => { vehicleTypes.value = res.data || [] })
    .catch(() => { vehicleTypes.value = [] })
  await nextTick()
  await initMap()
})

onBeforeUnmount(() => {
  clearVehicleMarkers()
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
})
</script>

<style scoped>
.hint {
  margin-bottom: 0.85rem;
  padding: 0.65rem 0.85rem;
  background: #fff8e8;
  border: 1px solid #f0d78c;
  border-radius: var(--radius);
  color: #8a6a12;
  font-size: 0.85rem;
}

.form-panel {
  padding: 1.25rem 1.35rem;
  max-width: none;
}

.create-layout {
  display: grid;
  grid-template-columns: minmax(280px, 400px) minmax(0, 1fr);
  gap: 1.25rem 1.5rem;
  align-items: stretch;
}

.form-col {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-width: 0;
}

.form-col label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.field-hint {
  font-size: 0.75rem;
  color: var(--text-secondary);
  line-height: 1.35;
}

.form-col input,
.form-col textarea,
.form-col select {
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  background: #fff;
}

.readonly-input {
  background: var(--bg-muted) !important;
  color: var(--text-secondary) !important;
  cursor: default;
}

.map-col {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.map-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-height: 0;
}

.map-hint {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.map-box {
  flex: 1;
  min-height: 420px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-muted);
}

.map-placeholder {
  flex: 1;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.25rem;
  text-align: center;
  font-size: 0.875rem;
  color: var(--text-secondary);
  background: var(--bg-muted);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
}

.form-actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 0.35rem;
}

@media (max-width: 960px) {
  .create-layout {
    grid-template-columns: 1fr;
  }

  .map-box {
    min-height: 300px;
  }
}
</style>
