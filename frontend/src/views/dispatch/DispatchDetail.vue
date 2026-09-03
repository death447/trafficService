<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>工单详情</h1>
        <p class="subtitle">{{ order?.orderNo || `工单 #${id}` }}</p>
      </div>
      <button type="button" class="secondary" @click="$router.push('/dispatches')">返回列表</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>

    <template v-else-if="order">
      <div class="panel info-panel">
        <div class="info-grid">
          <div>
            <span class="label">单号</span>
            <strong>{{ order.orderNo }}</strong>
          </div>
          <div>
            <span class="label">状态</span>
            <span :class="['badge', statusBadgeClass(order.status)]">
              {{ statusLabel(order.status) }}
            </span>
          </div>
          <div class="span-2">
            <span class="label">事故地点</span>
            <strong>{{ order.accidentAddress || '—' }}</strong>
          </div>
          <div>
            <span class="label">经度</span>
            <strong>{{ order.longitude ?? '—' }}</strong>
          </div>
          <div>
            <span class="label">纬度</span>
            <strong>{{ order.latitude ?? '—' }}</strong>
          </div>
          <div class="span-2">
            <span class="label">施救原因</span>
            <strong>{{ order.rescueReason || '—' }}</strong>
          </div>
          <div>
            <span class="label">调度员</span>
            <strong>{{ order.dispatcherName || order.dispatcherId || '—' }}</strong>
          </div>
          <div>
            <span class="label">车辆</span>
            <strong>{{ order.vehiclePlate || order.vehicleId || '—' }}</strong>
          </div>
          <div v-if="order.abortReason" class="span-2">
            <span class="label">中止原因</span>
            <strong>{{ order.abortReason }}</strong>
          </div>
          <div>
            <span class="label">创建时间</span>
            <strong>{{ formatTime(order.createTime) }}</strong>
          </div>
          <div>
            <span class="label">派单时间</span>
            <strong>{{ formatTime(order.dispatchedAt) }}</strong>
          </div>
        </div>
      </div>

      <!-- PENDING: map + nearby vehicles + assign -->
      <template v-if="order.status === 'PENDING'">
        <div class="assign-layout">
          <div class="panel map-panel">
            <h2 class="section-title">事故位置</h2>
            <div v-if="canShowMap" ref="mapEl" class="map-box" />
            <div v-else class="map-placeholder">
              <p v-if="!hasCoords">工单缺少坐标，无法在地图上展示事故点。</p>
              <p v-else-if="!amapReady">未配置 VITE_AMAP_KEY，地图不可用；请从下方列表派单。</p>
            </div>
            <p v-if="mapError" class="error">{{ mapError }}</p>
          </div>

          <div class="panel vehicle-panel">
            <h2 class="section-title">附近空闲车辆</h2>
            <p v-if="nearbyHint" class="hint-inline">{{ nearbyHint }}</p>
            <p v-if="nearbyError" class="error">{{ nearbyError }}</p>
            <p v-if="nearbyLoading" class="loading-text">加载附近车辆…</p>
            <ul v-else class="vehicle-list">
              <li
                v-for="item in nearby"
                :key="item.vehicle.id"
                :class="['vehicle-item', { selected: selectedVehicleId === item.vehicle.id }]"
                @click="selectedVehicleId = item.vehicle.id"
              >
                <div class="vehicle-main">
                  <strong>{{ item.vehicle.plateNo }}</strong>
                  <span class="muted">{{ vehicleTypeLabel(item.vehicle.vehicleType) }}</span>
                </div>
                <div class="vehicle-meta">
                  <span :class="['badge', 'badge-success']">空闲</span>
                  <span class="distance">{{ formatDistance(item.distanceMeters) }}</span>
                </div>
              </li>
              <li v-if="!nearby.length" class="empty-item">暂无空闲车辆</li>
            </ul>
            <div class="assign-actions">
              <button
                v-auth="'dispatch:abort'"
                type="button"
                class="danger"
                :disabled="!!acting"
                @click="openAbort"
              >
                中止工单
              </button>
              <button
                v-auth="'dispatch:dispatch'"
                type="button"
                :disabled="!selectedVehicleId || assigning"
                @click="onAssign"
              >
                {{ assigning ? '派单中…' : '确认派单' }}
              </button>
            </div>
            <p v-if="actionError" class="error">{{ actionError }}</p>
          </div>
        </div>
      </template>

      <!-- DISPATCHED: complete / abort -->
      <div v-else-if="order.status === 'DISPATCHED'" class="panel action-panel">
        <h2 class="section-title">工单操作</h2>
        <p class="muted">工单处理中，可标记完成或中止。</p>
        <div class="action-buttons">
          <button
            v-auth="'dispatch:complete'"
            type="button"
            :disabled="acting"
            @click="onComplete"
          >
            {{ acting === 'complete' ? '提交中…' : '完成' }}
          </button>
          <button
            v-auth="'dispatch:abort'"
            type="button"
            class="danger"
            :disabled="acting"
            @click="openAbort"
          >
            中止
          </button>
        </div>
        <p v-if="actionError" class="error">{{ actionError }}</p>
      </div>

      <!-- COMPLETED / ABORTED: read-only -->
      <div v-else class="panel action-panel">
        <p class="muted readonly-note">
          工单已{{ order.status === 'COMPLETED' ? '完成' : '中止' }}，仅可查看。
        </p>
      </div>
    </template>

    <div v-if="abortVisible" class="modal" @click.self="abortVisible = false">
      <form class="modal-card" @submit.prevent="onAbort">
        <h2>中止工单</h2>
        <label>
          中止原因
          <textarea
            v-model.trim="abortReason"
            rows="3"
            required
            placeholder="请填写中止原因"
          />
        </label>
        <p v-if="abortError" class="error">{{ abortError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="abortVisible = false">取消</button>
          <button type="submit" class="danger" :disabled="acting === 'abort'">
            {{ acting === 'abort' ? '提交中…' : '确认中止' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  getDispatch,
  assignDispatch,
  completeDispatch,
  abortDispatch
} from '../../api/dispatch'
import { nearbyVehicles, listVehicles } from '../../api/vehicle'
import { hasAmapKey, loadAmap } from '../../utils/amap'

const route = useRoute()
const id = computed(() => route.params.id)

const order = ref(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const assigning = ref(false)
const acting = ref('')

const nearby = ref([])
const nearbyLoading = ref(false)
const nearbyError = ref('')
const nearbyHint = ref('')
const selectedVehicleId = ref(null)

const mapEl = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
let mapInstance = null

const abortVisible = ref(false)
const abortReason = ref('')
const abortError = ref('')

const statusLabels = {
  PENDING: '待派单',
  DISPATCHED: '处理中',
  COMPLETED: '已完成',
  ABORTED: '已中止'
}

const vehicleTypeLabels = {
  TOW: '拖车',
  CLEARANCE: '清障车',
  OTHER: '其他'
}

const hasCoords = computed(() => {
  const o = order.value
  return o && o.longitude != null && o.latitude != null && o.longitude !== '' && o.latitude !== ''
})

const canShowMap = computed(() => amapReady && hasCoords.value)

function statusLabel(status) {
  return statusLabels[status] || status || '—'
}

function statusBadgeClass(status) {
  if (status === 'PENDING' || status === 'DISPATCHED') return 'badge-info'
  if (status === 'COMPLETED') return 'badge-success'
  return 'badge-muted'
}

function vehicleTypeLabel(type) {
  return vehicleTypeLabels[type] || type || '—'
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatDistance(meters) {
  if (meters == null || Number.isNaN(Number(meters))) return '—'
  const m = Number(meters)
  if (m < 1000) return `${Math.round(m)} m`
  return `${(m / 1000).toFixed(1)} km`
}

async function loadOrder() {
  loading.value = true
  error.value = ''
  actionError.value = ''
  try {
    const res = await getDispatch(id.value)
    order.value = res.data
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载工单失败'
    order.value = null
  } finally {
    loading.value = false
  }
}

async function loadNearby() {
  nearbyLoading.value = true
  nearbyError.value = ''
  nearbyHint.value = ''
  selectedVehicleId.value = null
  nearby.value = []
  try {
    if (hasCoords.value) {
      const res = await nearbyVehicles({
        lng: order.value.longitude,
        lat: order.value.latitude,
        limit: 20
      })
      nearby.value = res.data || []
      if (!amapReady) {
        nearbyHint.value = '未配置地图 Key，已按距离排序展示附近空闲车辆。'
      }
    } else {
      nearbyHint.value = '工单无坐标，展示空闲车辆列表（无距离排序）。'
      const res = await listVehicles({ status: 'IDLE' })
      const list = res.data?.list || []
      nearby.value = list.slice(0, 20).map((vehicle) => ({
        vehicle,
        distanceMeters: null
      }))
    }
  } catch (e) {
    nearbyError.value = e.response?.data?.message || e.message || '加载附近车辆失败'
  } finally {
    nearbyLoading.value = false
  }
}

async function initMap() {
  destroyMap()
  mapError.value = ''
  if (!canShowMap.value || !mapEl.value) return
  try {
    const AMap = await loadAmap()
    const lng = Number(order.value.longitude)
    const lat = Number(order.value.latitude)
    mapInstance = new AMap.Map(mapEl.value, {
      zoom: 14,
      center: [lng, lat]
    })
    new AMap.Marker({ position: [lng, lat], map: mapInstance })
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

function destroyMap() {
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
}

async function onAssign() {
  if (!selectedVehicleId.value) return
  assigning.value = true
  actionError.value = ''
  try {
    const res = await assignDispatch(id.value, { vehicleId: selectedVehicleId.value })
    order.value = res.data
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message || '派单失败'
  } finally {
    assigning.value = false
  }
}

async function onComplete() {
  acting.value = 'complete'
  actionError.value = ''
  try {
    const res = await completeDispatch(id.value)
    order.value = res.data
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message || '完成失败'
  } finally {
    acting.value = ''
  }
}

function openAbort() {
  abortReason.value = ''
  abortError.value = ''
  abortVisible.value = true
}

async function onAbort() {
  if (!abortReason.value.trim()) {
    abortError.value = '请填写中止原因'
    return
  }
  acting.value = 'abort'
  abortError.value = ''
  try {
    const res = await abortDispatch(id.value, { abortReason: abortReason.value.trim() })
    order.value = res.data
    abortVisible.value = false
  } catch (e) {
    abortError.value = e.response?.data?.message || e.message || '中止失败'
  } finally {
    acting.value = ''
  }
}

watch(
  () => order.value?.status,
  async (status) => {
    destroyMap()
    if (status === 'PENDING') {
      await loadNearby()
      await nextTick()
      await initMap()
    }
  }
)

watch(
  () => route.params.id,
  async () => {
    destroyMap()
    nearby.value = []
    selectedVehicleId.value = null
    await loadOrder()
  }
)

onMounted(async () => {
  await loadOrder()
})

onBeforeUnmount(() => {
  destroyMap()
})
</script>

<style scoped>
.loading-text {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.info-panel {
  padding: 1.15rem 1.25rem;
  margin-bottom: 1rem;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem 1.25rem;
}

.info-grid .span-2 {
  grid-column: 1 / -1;
}

.label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 0.25rem;
}

.info-grid strong {
  font-size: 0.9rem;
  font-weight: 600;
}

.assign-layout {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 1rem;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
}

.map-panel,
.vehicle-panel,
.action-panel {
  padding: 1.1rem 1.2rem;
}

.map-box {
  height: 360px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-muted);
}

.map-placeholder {
  min-height: 180px;
  padding: 1.25rem;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg-muted);
  color: var(--text-secondary);
  font-size: 0.875rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  justify-content: center;
}

.hint-inline {
  font-size: 0.8rem;
  color: #8a6a12;
  margin-bottom: 0.65rem;
}

.vehicle-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.vehicle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.12s ease;
}

.vehicle-item:last-child {
  border-bottom: none;
}

.vehicle-item:hover {
  background: #f5f9fc;
}

.vehicle-item.selected {
  background: var(--accent-soft);
  outline: 1px solid #9cc7e6;
}

.vehicle-main {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.vehicle-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
}

.distance {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.muted {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.empty-item {
  padding: 1.25rem;
  text-align: center;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.assign-actions {
  margin-top: 0.85rem;
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

.action-buttons {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.85rem;
}

.readonly-note {
  margin: 0;
}

.modal-card textarea {
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  resize: vertical;
}

@media (max-width: 900px) {
  .assign-layout {
    grid-template-columns: 1fr;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-grid .span-2 {
    grid-column: auto;
  }
}
</style>
