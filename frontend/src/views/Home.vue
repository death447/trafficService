<template>
  <div class="page home">
    <section v-if="canQueryDispatch" class="stat-row">
      <div class="stat-card">
        <span class="stat-kicker">今日</span>
        <span class="stat-label">事故总数</span>
        <strong>{{ displayNum(overview.todayTotal) }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-kicker">今日</span>
        <span class="stat-label">已派单</span>
        <strong>{{ displayNum(overview.todayDispatched) }}</strong>
      </div>
      <div class="stat-card accent">
        <span class="stat-kicker">当前</span>
        <span class="stat-label">处置中</span>
        <strong>{{ displayNum(overview.inProgress) }}</strong>
      </div>
      <div class="stat-card success">
        <span class="stat-kicker">今日</span>
        <span class="stat-label">已完成</span>
        <strong>{{ displayNum(overview.todayCompleted) }}</strong>
      </div>
    </section>
    <p v-if="canQueryDispatch && overviewError" class="error">{{ overviewError }}</p>

    <section v-if="canQueryDispatch || canQueryVehicle" class="map-section panel">
      <div class="map-head">
        <div>
          <h2 class="section-title">实时态势</h2>
          <p class="map-sub">标注处置中的事故点与空闲救援车辆</p>
        </div>
        <div class="legend">
          <span v-if="canQueryDispatch" class="legend-item"><i class="dot acc" />事故点 {{ accidents.length }}</span>
          <span v-if="canQueryVehicle" class="legend-item"><i class="dot veh" />空闲车辆 {{ idleVehicles.length }}</span>
        </div>
      </div>
      <div v-if="canShowMap" ref="mapEl" class="map-box" />
      <div v-else class="map-placeholder">
        <p v-if="!amapReady">未配置 VITE_AMAP_KEY，地图不可用。</p>
        <p v-else>暂无坐标可展示。</p>
      </div>
      <p v-if="mapError" class="error">{{ mapError }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getDispatchOverview } from '../api/dispatch'
import { listVehicles } from '../api/vehicle'
import { createVehicleMapIcon, hasAmapKey, loadAmap } from '../utils/amap'

const JIAXING_CENTER = [120.755486, 30.746849]
const JIAXING_ZOOM = 13
const MAP_MAX_ZOOM = 13

const store = useUserStore()
const router = useRouter()

const canQueryDispatch = computed(() => store.hasPermission('dispatch:query'))
const canQueryVehicle = computed(() => store.hasPermission('vehicle:query'))

const overview = reactive({
  todayTotal: 0,
  todayDispatched: 0,
  todayCompleted: 0,
  inProgress: 0
})
const accidents = ref([])
const idleVehicles = ref([])
const overviewError = ref('')
const mapEl = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
const canShowMap = computed(() => amapReady)
let mapInstance = null
let accidentMarkers = []
let vehicleMarkers = []
let pollTimer = null

const statusLabels = {
  PENDING: '待派单',
  DISPATCHED: '已派单',
  ACCEPTED: '已接单'
}

function displayNum(n) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  return String(n)
}

function hasCoords(item) {
  return item && item.longitude != null && item.latitude != null
    && item.longitude !== '' && item.latitude !== ''
}

async function loadOverview() {
  overviewError.value = ''
  if (canQueryDispatch.value) {
    try {
      const res = await getDispatchOverview()
      const data = res.data || {}
      overview.todayTotal = data.todayTotal || 0
      overview.todayDispatched = data.todayDispatched || 0
      overview.todayCompleted = data.todayCompleted || 0
      overview.inProgress = data.inProgress || 0
      accidents.value = (data.accidents || []).filter(hasCoords)
    } catch (e) {
      overviewError.value = e.response?.data?.message || e.message || '加载概览失败'
    }
  }
  if (canQueryVehicle.value) {
    try {
      const res = await listVehicles({ status: 'IDLE', page: 1, size: 100 })
      idleVehicles.value = (res.data?.list || []).filter(hasCoords)
    } catch (_) {
      idleVehicles.value = []
    }
  }
}

function clearMarkers(list) {
  list.forEach((m) => {
    if (m && typeof m.setMap === 'function') m.setMap(null)
  })
  list.length = 0
}

function syncMarkers() {
  if (!mapInstance || !window.AMap) return
  const AMap = window.AMap
  clearMarkers(accidentMarkers)
  clearMarkers(vehicleMarkers)

  accidents.value.forEach((o) => {
    const lng = Number(o.longitude)
    const lat = Number(o.latitude)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
    const marker = new AMap.Marker({
      position: [lng, lat],
      map: mapInstance,
      content: '<div class="ov-acc-pin"></div>',
      offset: new AMap.Pixel(-9, -9),
      title: o.orderNo || '',
      label: {
        content: `${o.orderNo || ''} ${statusLabels[o.status] || ''}`.trim(),
        direction: 'top',
        offset: new AMap.Pixel(0, -4)
      }
    })
    marker.on('click', () => {
      if (o.id) router.push(`/dispatches/${o.id}`)
    })
    accidentMarkers.push(marker)
  })

  const icon = createVehicleMapIcon(AMap)
  idleVehicles.value.forEach((v) => {
    const lng = Number(v.longitude)
    const lat = Number(v.latitude)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
    const marker = new AMap.Marker({
      position: [lng, lat],
      map: mapInstance,
      icon,
      offset: new AMap.Pixel(-22, -18),
      title: v.plateNo || '',
      label: {
        content: v.plateNo || '空闲',
        direction: 'top',
        offset: new AMap.Pixel(0, -4)
      }
    })
    vehicleMarkers.push(marker)
  })

  const overlays = [...accidentMarkers, ...vehicleMarkers]
  if (overlays.length) {
    mapInstance.setFitView(overlays, false, [80, 80, 80, 80], MAP_MAX_ZOOM)
    const zoom = mapInstance.getZoom()
    if (typeof zoom === 'number') {
      mapInstance.setZoom(Math.min(Math.max(zoom, JIAXING_ZOOM), MAP_MAX_ZOOM))
    }
  } else {
    mapInstance.setZoomAndCenter(JIAXING_ZOOM, JIAXING_CENTER)
  }
}

async function initMap() {
  destroyMap()
  mapError.value = ''
  if (!canShowMap.value || !mapEl.value) return
  try {
    const AMap = await loadAmap()
    mapInstance = new AMap.Map(mapEl.value, {
      zoom: JIAXING_ZOOM,
      center: JIAXING_CENTER,
      resizeEnable: true
    })
    syncMarkers()
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

function destroyMap() {
  clearMarkers(accidentMarkers)
  clearMarkers(vehicleMarkers)
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(async () => {
    await loadOverview()
    syncMarkers()
  }, 20000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  await loadOverview()
  await nextTick()
  await initMap()
  startPoll()
})

onBeforeUnmount(() => {
  stopPoll()
  destroyMap()
})
</script>

<style>
.ov-acc-pin {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #e11d48;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px rgba(225, 29, 72, 0.35);
}
</style>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.stat-label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 0.35rem;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

.stat-card {
  padding: 1.1rem 1.2rem;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.stat-kicker {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  color: var(--text-secondary);
  margin-bottom: 0.2rem;
}

.stat-card .stat-label {
  margin-bottom: 0.45rem;
}

.stat-card strong {
  font-size: 1.85rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text);
}

.stat-card.accent strong {
  color: var(--accent-hover);
}

.stat-card.success strong {
  color: var(--success);
}

.map-section {
  padding: 1.1rem 1.2rem 1.2rem;
}

.map-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 1rem;
  margin-bottom: 0.85rem;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  margin: 0 0 0.2rem;
}

.map-sub {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.legend {
  display: flex;
  gap: 1rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.dot.acc {
  background: #e11d48;
}

.dot.veh {
  background: #f5c518;
  border: 1px solid #c9a000;
}

.map-box {
  height: min(56vh, 580px);
  min-height: 380px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-muted);
}

.map-placeholder {
  min-height: 280px;
  padding: 1.25rem;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg-muted);
  color: var(--text-secondary);
  font-size: 0.875rem;
  display: flex;
  align-items: center;
}

.error {
  color: var(--danger);
  font-size: 0.85rem;
}

@media (max-width: 960px) {
  .stat-row {
    grid-template-columns: 1fr 1fr;
  }

  .map-box {
    min-height: 280px;
    height: 320px;
  }
}
</style>
