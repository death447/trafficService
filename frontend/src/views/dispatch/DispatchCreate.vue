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
          <div class="accident-vehicles">
            <div class="accident-vehicles-head">
              <span class="accident-vehicles-title">事故车辆（至少 1 辆，车牌必填）</span>
              <button type="button" class="secondary small" @click="addAccidentVehicle">添加车辆</button>
            </div>
            <div
              v-for="(av, idx) in accidentVehicles"
              :key="idx"
              class="accident-vehicle-row"
            >
              <label>
                车牌号码
                <input v-model.trim="av.plateNo" required placeholder="必填" maxlength="20" />
              </label>
              <label>
                车型
                <select v-model="av.vehicleTypeId">
                  <option value="">不选择</option>
                  <option v-for="t in vehicleTypes" :key="t.id" :value="String(t.id)">
                    {{ t.name }}
                  </option>
                </select>
              </label>
              <label>
                事故当事人
                <input v-model.trim="av.partyName" placeholder="选填" maxlength="50" />
              </label>
              <label>
                手机号码
                <input v-model.trim="av.partyPhone" placeholder="选填" maxlength="20" />
              </label>
              <button
                type="button"
                class="secondary small"
                :disabled="accidentVehicles.length <= 1"
                @click="removeAccidentVehicle(idx)"
              >
                删除
              </button>
            </div>
          </div>
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
          <label class="vehicle-pick">
            救援车辆
            <div class="vehicle-pick-toolbar">
              <button
                type="button"
                class="linkish"
                :disabled="!selectedVehicleIds.length"
                @click="clearSelectedVehicles"
              >
                清空已选
              </button>
              <span class="field-hint">可选多辆；保存时立即派单。不选则仅建待派工单。</span>
            </div>
            <ul class="vehicle-check-list">
              <li v-for="v in selectableVehicles" :key="v.id">
                <label>
                  <input
                    type="checkbox"
                    :checked="isVehicleSelected(v.id)"
                    @change="toggleVehicle(v.id)"
                  />
                  {{ vehicleDisplay(v) }}
                </label>
              </li>
            </ul>
            <span v-if="nearbyHint" class="field-hint">{{ nearbyHint }}</span>
          </label>
          <label>
            施救员
            <div v-if="!selectedVehicleIds.length" class="readonly-input rescuer-empty">
              请先选择救援车辆
            </div>
            <ul v-else class="rescuer-summary">
              <li v-for="row in selectedVehicleSummaries" :key="row.id">
                {{ row.plate }} → {{ row.rescuer }}
              </li>
            </ul>
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
              输入地址回车或地图选点定位事故；定位后地图会标注附近可派车辆，点击标记可多选/取消
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
import { clearDistrictOverlays, createPickerMap, createVehicleMapIcon, drawDistrictOverlays, hasAmapKey } from '../../utils/amap'
import { loadEnabledDistrictOverlayItems } from '../../utils/districtOverlay'

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
let districtHandles = null

const form = reactive({
  rescueReason: '',
  accidentAddress: '',
  longitude: '',
  latitude: ''
})

const selectedVehicleIds = ref([])

function toggleVehicle(id) {
  const s = String(id)
  const i = selectedVehicleIds.value.indexOf(s)
  if (i >= 0) selectedVehicleIds.value.splice(i, 1)
  else selectedVehicleIds.value.push(s)
}

function clearSelectedVehicles() {
  selectedVehicleIds.value = []
}

function isVehicleSelected(id) {
  return selectedVehicleIds.value.includes(String(id))
}

function emptyAccidentVehicle() {
  return { plateNo: '', vehicleTypeId: '', partyName: '', partyPhone: '' }
}

const accidentVehicles = reactive([emptyAccidentVehicle()])

function addAccidentVehicle() {
  accidentVehicles.push(emptyAccidentVehicle())
}

function removeAccidentVehicle(idx) {
  if (accidentVehicles.length <= 1) return
  accidentVehicles.splice(idx, 1)
}

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

/** ids known to have active assignment (nearby flag or BUSY status) */
function isBusyVehicle(v, nearbyItem) {
  if (nearbyItem?.hasActiveAssignment) return true
  if (v?.status === 'BUSY') return true
  return false
}

/**
 * 可选车辆：空闲 + 已有进行中派单（BUSY）均可选；
 * 附近距离优先，空闲排前、已派单靠后。
 */
const selectableVehicles = computed(() => {
  const byId = new Map(
    vehicles.value
      .filter((v) => v.status === 'IDLE' || v.status === 'BUSY')
      .map((v) => [v.id, v])
  )
  // merge vehicles only seen in nearby (status may be BUSY)
  for (const item of [...nearbyFresh.value, ...nearbyStale.value]) {
    const v = item.vehicle
    if (v?.id != null && !byId.has(v.id) && v.status !== 'OFFLINE') {
      byId.set(v.id, v)
    }
  }
  const ordered = []
  const seen = new Set()
  const nearbyOrdered = [...nearbyFresh.value, ...nearbyStale.value].sort((a, b) => {
    const aBusy = a.hasActiveAssignment ? 1 : 0
    const bBusy = b.hasActiveAssignment ? 1 : 0
    if (aBusy !== bBusy) return aBusy - bBusy
    const ad = a.distanceMeters
    const bd = b.distanceMeters
    if (ad == null && bd == null) return 0
    if (ad == null) return 1
    if (bd == null) return -1
    return ad - bd
  })
  for (const item of nearbyOrdered) {
    const id = item.vehicle?.id
    if (id == null || seen.has(id) || !byId.has(id)) continue
    ordered.push(byId.get(id))
    seen.add(id)
  }
  // remaining: idle first, then busy
  const rest = [...byId.values()].filter((v) => !seen.has(v.id))
  rest.sort((a, b) => {
    const aBusy = a.status === 'BUSY' ? 1 : 0
    const bBusy = b.status === 'BUSY' ? 1 : 0
    return aBusy - bBusy
  })
  ordered.push(...rest)
  return ordered
})

const selectedVehicleSummaries = computed(() => {
  return selectedVehicleIds.value.map((id) => {
    const v = selectableVehicles.value.find((x) => String(x.id) === id)
    if (!v) return { id, plate: `#${id}`, rescuer: '—' }
    const rescuer =
      v.driverName ||
      (v.driverUserId != null ? `用户#${v.driverUserId}` : '该车辆未绑定施救员')
    return { id, plate: v.plateNo || `#${id}`, rescuer }
  })
})

watch(selectedVehicleIds, () => syncVehicleMarkers(), { deep: true })

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
  const nearbyItem = [...nearbyFresh.value, ...nearbyStale.value].find(
    (i) => i.vehicle?.id === v.id
  )
  const statusPart = isBusyVehicle(v, nearbyItem) ? '任务中' : '空闲'
  return `${v.plateNo}（${statusPart} · ${driver}${distPart}）`
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
    if (v.status === 'OFFLINE') continue
    const busy = isBusyVehicle(v, item)
    const selected = isVehicleSelected(v.id)
    const marker = new AMap.Marker({
      position: [Number(v.longitude), Number(v.latitude)],
      map,
      icon,
      offset: new AMap.Pixel(-22, -18),
      title: v.plateNo || '',
      label: {
        content: `${selected ? '✓ ' : ''}${v.plateNo || ''}${busy ? '（任务中）' : ''} ${formatDistance(item.distanceMeters)}`,
        direction: 'top',
        offset: new AMap.Pixel(0, -4)
      }
    })
    marker.on('click', () => {
      toggleVehicle(v.id)
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

function goBack() {
  router.push('/dispatches')
}

async function loadLookups() {
  lookupError.value = ''
  try {
    const [idleRes, busyRes] = await Promise.all([
      listVehicles({ status: 'IDLE', page: 1, size: 100 }),
      listVehicles({ status: 'BUSY', page: 1, size: 100 })
    ])
    const byId = new Map()
    for (const v of [...(idleRes.data?.list || []), ...(busyRes.data?.list || [])]) {
      byId.set(v.id, v)
    }
    vehicles.value = [...byId.values()]
  } catch (e) {
    vehicles.value = []
    lookupError.value =
      e?.response?.data?.message || e?.message || '加载可派车辆失败'
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
    const districtItems = await loadEnabledDistrictOverlayItems()
    districtHandles = drawDistrictOverlays(mapInstance.map, districtItems)
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
  const vehiclesPayload = accidentVehicles
    .map((av) => ({
      plateNo: (av.plateNo || '').trim(),
      vehicleTypeId: av.vehicleTypeId ? Number(av.vehicleTypeId) : null,
      partyName: (av.partyName || '').trim() || null,
      partyPhone: (av.partyPhone || '').trim() || null
    }))
    .filter((av) => av.plateNo)
  if (vehiclesPayload.length === 0) {
    formError.value = '请至少添加一辆事故车并填写车牌'
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
      vehicleIds: selectedVehicleIds.value
        .map((id) => Number(id))
        .filter((n) => !Number.isNaN(n)),
      accidentVehicles: vehiclesPayload
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
  if (mapInstance?.map) {
    clearDistrictOverlays(mapInstance.map, districtHandles)
  }
  districtHandles = null
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

.accident-vehicles {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.accident-vehicles-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.accident-vehicles-title {
  font-size: 0.85rem;
  color: var(--text-secondary);
  font-weight: 600;
}

.accident-vehicle-row {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fafbfc;
}

button.small {
  padding: 0.3rem 0.65rem;
  font-size: 0.8rem;
  align-self: flex-start;
}

.field-hint {
  font-size: 0.75rem;
  color: var(--text-secondary);
  line-height: 1.35;
}

.vehicle-pick-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
}

button.linkish {
  border: none;
  background: none;
  padding: 0;
  color: var(--accent, #1a6fb5);
  font-size: 0.8rem;
  cursor: pointer;
  text-decoration: underline;
}

button.linkish:disabled {
  color: var(--text-secondary);
  cursor: default;
  text-decoration: none;
}

.vehicle-check-list {
  list-style: none;
  margin: 0;
  padding: 0.35rem 0.5rem;
  max-height: 12rem;
  overflow: auto;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: #fff;
}

.vehicle-check-list li label {
  flex-direction: row;
  align-items: flex-start;
  gap: 0.45rem;
  font-size: 0.8rem;
  color: var(--text);
  cursor: pointer;
  padding: 0.25rem 0;
}

.vehicle-check-list input[type='checkbox'] {
  margin-top: 0.15rem;
  flex-shrink: 0;
}

.rescuer-empty {
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
}

.rescuer-summary {
  list-style: none;
  margin: 0;
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-muted);
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.rescuer-summary li + li {
  margin-top: 0.25rem;
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
