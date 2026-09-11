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
      <div class="detail-layout" :class="{ 'rated-layout': showEvalBlock }">
        <div class="detail-left">
          <div class="panel info-panel order-info">
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
                <span class="label">车牌号码</span>
                <strong>{{ order.plateNo || '—' }}</strong>
              </div>
              <div>
                <span class="label">车型</span>
                <strong>{{ order.vehicleTypeName || '—' }}</strong>
              </div>
              <div>
                <span class="label">当事人</span>
                <strong>{{ order.partyName || '—' }}</strong>
              </div>
              <div>
                <span class="label">联系方式</span>
                <strong>{{ order.partyPhone || '—' }}</strong>
              </div>
              <div>
                <span class="label">调度员</span>
                <strong>{{ order.dispatcherName || order.dispatcherId || '—' }}</strong>
              </div>
              <div>
                <span class="label">施救员</span>
                <strong>{{ order.rescuerName || order.rescuerId || '—' }}</strong>
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

          <div v-if="hasSceneCollection" class="panel info-panel scene-panel">
            <h2 class="section-title">现场采集</h2>
            <div class="info-grid">
              <div>
                <span class="label">车牌号码</span>
                <strong>{{ fieldRecord?.plateNo || '—' }}</strong>
              </div>
              <div>
                <span class="label">车型</span>
                <strong>{{ fieldRecord?.vehicleType || '—' }}</strong>
              </div>
              <div v-if="fieldRecord?.sceneSubmittedAt">
                <span class="label">采集时间</span>
                <strong>{{ formatTime(fieldRecord.sceneSubmittedAt) }}</strong>
              </div>
              <div class="span-2">
                <span class="label">受损描述</span>
                <strong>{{ fieldRecord?.damageDesc || '—' }}</strong>
              </div>
              <div class="span-2">
                <span class="label">现场备注</span>
                <strong>{{ fieldRecord?.sceneRemark || '—' }}</strong>
              </div>
              <div v-if="damageMedias.length" class="span-2">
                <span class="label">受损照片</span>
                <div class="photo-grid">
                  <button
                    v-for="m in damageMedias"
                    :key="m.id"
                    type="button"
                    class="photo-thumb"
                    @click="previewSrc = mediaUrl(m.filePath)"
                  >
                    <img :src="mediaUrl(m.filePath)" :alt="m.filePath || '受损照片'" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- PENDING: edit + nearby vehicles + assign -->
          <template v-if="order.status === 'PENDING'">
            <div class="panel edit-panel">
              <h2 class="section-title">车牌、车型与当事人</h2>
              <div class="edit-form">
                <label>
                  车牌号码
                  <input v-model.trim="editForm.plateNo" placeholder="选填" maxlength="20" />
                </label>
                <label>
                  车型
                  <select v-model="editForm.vehicleTypeId">
                    <option value="">不选择</option>
                    <option v-for="t in editVehicleTypeOptions" :key="t.id" :value="String(t.id)">
                      {{ t.name }}
                    </option>
                  </select>
                </label>
                <label>
                  事故当事人
                  <input v-model.trim="editForm.partyName" placeholder="选填" maxlength="50" />
                </label>
                <label>
                  手机号码
                  <input v-model.trim="editForm.partyPhone" placeholder="选填" maxlength="20" />
                </label>
                <button type="button" :disabled="savingEdit" @click="onSaveEdit">
                  {{ savingEdit ? '保存中…' : '保存' }}
                </button>
              </div>
              <p v-if="editError" class="error">{{ editError }}</p>
            </div>

            <div class="panel vehicle-panel">
              <h2 class="section-title">附近空闲车辆</h2>
              <p v-if="matchedDistrict" class="hint-inline">
                所属片区：{{ matchedDistrict.name }}（{{ matchedDistrict.code }}）
              </p>
              <p v-else-if="hasCoords && !nearbyLoading" class="hint-inline">未匹配到片区</p>
              <p v-if="nearbyHint" class="hint-inline">{{ nearbyHint }}</p>
              <p v-if="nearbyPollHint" class="hint-inline">{{ nearbyPollHint }}</p>
              <p v-if="nearbyError" class="error">{{ nearbyError }}</p>
              <p v-if="nearbyLoading" class="loading-text">加载附近车辆…</p>
              <template v-else>
                <div
                  v-for="section in vehicleSections"
                  :key="section.key"
                  class="vehicle-section"
                >
                  <div class="subsection-header">
                    <h3 class="subsection-title">{{ section.title }}</h3>
                    <button
                      v-if="section.collapsible"
                      type="button"
                      class="secondary toggle-stale"
                      @click="staleExpanded = !staleExpanded"
                    >
                      {{ staleExpanded ? '收起' : '展开' }}
                    </button>
                  </div>
                  <ul
                    v-if="!section.collapsible || staleExpanded"
                    class="vehicle-list"
                  >
                    <li
                      v-for="item in section.items"
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
                    <li v-if="!section.items.length" class="empty-item">{{ section.emptyText }}</li>
                  </ul>
                </div>
              </template>
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
          </template>

          <!-- DISPATCHED / ACCEPTED: complete / abort -->
          <div
            v-if="order.status === 'DISPATCHED' || order.status === 'ACCEPTED'"
            class="panel action-panel"
          >
            <h2 class="section-title">工单操作</h2>
            <p class="muted">
              {{ order.status === 'ACCEPTED' ? '工单已接单，可标记完成或中止。' : '工单已派单，可标记完成或中止。' }}
            </p>
            <div class="action-buttons">
              <button
                v-auth="'dispatch:complete'"
                type="button"
                :disabled="!!acting"
                @click="onComplete"
              >
                {{ acting === 'complete' ? '提交中…' : '完成' }}
              </button>
              <button
                v-auth="'dispatch:abort'"
                type="button"
                class="danger"
                :disabled="!!acting"
                @click="openAbort"
              >
                中止
              </button>
            </div>
            <p v-if="actionError" class="error">{{ actionError }}</p>
          </div>

          <!-- COMPLETED / ABORTED: read-only -->
          <div
            v-else-if="order.status === 'COMPLETED' || order.status === 'ABORTED'"
            class="panel action-panel"
          >
            <p class="muted readonly-note">
              工单已{{ order.status === 'COMPLETED' ? '完成' : '中止' }}，仅可查看。
            </p>
          </div>
        </div>

        <div class="detail-right">
          <!-- PENDING: accident + nearby map -->
          <div v-if="order.status === 'PENDING'" class="panel map-panel">
            <h2 class="section-title">事故位置</h2>
            <div v-if="canShowMap" ref="mapEl" class="map-box" />
            <div v-else class="map-placeholder">
              <p v-if="!hasCoords">工单缺少坐标，无法在地图上展示事故点。</p>
              <p v-else-if="!amapReady">未配置 VITE_AMAP_KEY，地图不可用；请从左侧列表派单。</p>
            </div>
            <p v-if="mapError" class="error">{{ mapError }}</p>
          </div>

          <!-- Non-PENDING: location tracking map -->
          <div v-else class="panel map-panel track-map-panel">
            <h2 class="section-title">位置跟踪</h2>
            <div v-if="canShowMap" ref="trackMapEl" class="map-box" />
            <div v-else class="map-placeholder">
              <p v-if="!hasCoords">工单缺少坐标，无法在地图上展示。</p>
              <p v-else-if="!amapReady">未配置 VITE_AMAP_KEY，地图不可用。</p>
            </div>
            <p v-if="trackHint" class="hint-inline">{{ trackHint }}</p>
            <p v-if="trackPollHint" class="hint-inline">{{ trackPollHint }}</p>
            <p v-if="mapError" class="error">{{ mapError }}</p>
          </div>

          <div v-if="evaluation" class="panel info-panel eval-panel">
            <h2 class="section-title">评价</h2>
            <div class="info-grid">
              <div>
                <span class="label">到达及时</span>
                <span class="stars" :aria-label="`${evaluation.scorePunctual} 星`">
                  <span
                    v-for="n in 5"
                    :key="'p' + n"
                    class="star"
                    :class="{ on: starOn(evaluation.scorePunctual, n) }"
                    aria-hidden="true"
                  >★</span>
                </span>
              </div>
              <div>
                <span class="label">处置规范</span>
                <span class="stars" :aria-label="`${evaluation.scoreStandard} 星`">
                  <span
                    v-for="n in 5"
                    :key="'st' + n"
                    class="star"
                    :class="{ on: starOn(evaluation.scoreStandard, n) }"
                    aria-hidden="true"
                  >★</span>
                </span>
              </div>
              <div>
                <span class="label">操作安全</span>
                <span class="stars" :aria-label="`${evaluation.scoreSafety} 星`">
                  <span
                    v-for="n in 5"
                    :key="'sa' + n"
                    class="star"
                    :class="{ on: starOn(evaluation.scoreSafety, n) }"
                    aria-hidden="true"
                  >★</span>
                </span>
              </div>
              <div>
                <span class="label">服务态度</span>
                <span class="stars" :aria-label="`${evaluation.scoreAttitude} 星`">
                  <span
                    v-for="n in 5"
                    :key="'at' + n"
                    class="star"
                    :class="{ on: starOn(evaluation.scoreAttitude, n) }"
                    aria-hidden="true"
                  >★</span>
                </span>
              </div>
              <div class="span-2">
                <span class="label">意见</span>
                <strong>{{ evaluation.comment || '无' }}</strong>
              </div>
            </div>
          </div>

          <div
            v-else-if="order.status === 'COMPLETED'"
            class="panel info-panel eval-panel"
          >
            <h2 class="section-title">评价</h2>
            <p class="muted readonly-note">暂无评价</p>
          </div>
        </div>
      </div>
    </template>

    <div v-if="previewSrc" class="modal photo-preview" @click.self="previewSrc = ''">
      <img :src="previewSrc" alt="受损照片预览" />
    </div>

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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  getDispatch,
  updateDispatch,
  assignDispatch,
  completeDispatch,
  abortDispatch
} from '../../api/dispatch'
import { nearbyVehicles, listVehicles, getVehicle } from '../../api/vehicle'
import { listEnabledVehicleTypes } from '../../api/vehicleType'
import { hasAmapKey, loadAmap, createVehicleMapIcon } from '../../utils/amap'

const route = useRoute()
const id = computed(() => route.params.id)

const order = ref(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const assigning = ref(false)
const acting = ref('')

const nearby = ref([])
const staleNearby = ref([])
const staleExpanded = ref(false)
const matchedDistrict = ref(null)
const nearbyLoading = ref(false)
const nearbyError = ref('')
const nearbyHint = ref('')
const nearbyPollHint = ref('')
const selectedVehicleId = ref(null)

const vehicleTypes = ref([])

const editVehicleTypeOptions = computed(() => {
  const enabled = vehicleTypes.value
  const currentId = editForm.vehicleTypeId
  if (!currentId || enabled.some((t) => String(t.id) === currentId)) {
    return enabled
  }
  const o = order.value
  if (o?.vehicleTypeId != null && String(o.vehicleTypeId) === currentId && o.vehicleTypeName) {
    return [{ id: o.vehicleTypeId, name: `${o.vehicleTypeName}（已停用）` }, ...enabled]
  }
  return enabled
})

const editForm = reactive({
  plateNo: '',
  vehicleTypeId: '',
  partyName: '',
  partyPhone: ''
})
const savingEdit = ref(false)
const editError = ref('')
const previewSrc = ref('')

const fieldRecord = computed(() => order.value?.fieldRecord || null)
const evaluation = computed(() => order.value?.evaluation || null)
const showEvalBlock = computed(
  () => !!evaluation.value || order.value?.status === 'COMPLETED'
)
const damageMedias = computed(() =>
  (order.value?.medias || []).filter((m) => m.bizType === 'DAMAGE')
)
const hasSceneCollection = computed(() => {
  const fr = fieldRecord.value
  const hasText = Boolean(
    fr && (fr.plateNo || fr.vehicleType || fr.damageDesc || fr.sceneRemark || fr.sceneSubmittedAt)
  )
  return hasText || damageMedias.value.length > 0
})

function mediaUrl(filePath) {
  if (!filePath) return ''
  if (/^https?:\/\//i.test(filePath)) return filePath
  const path = String(filePath).replace(/^\/+/, '')
  return `/uploads/${path}`
}

const vehicleSections = computed(() => [
  {
    key: 'fresh',
    title: '附近空闲（实时）',
    items: nearby.value,
    emptyText: '暂无实时定位的空闲车辆',
    collapsible: false
  },
  {
    key: 'stale',
    title: '位置未知 / 过期',
    items: staleNearby.value,
    emptyText: '无',
    collapsible: true
  }
])

const mapEl = ref(null)
const trackMapEl = ref(null)
const trackHint = ref('')
const trackPollHint = ref('')
const trackedVehicle = ref(null) // { id, plateNo, longitude, latitude, ... }
const amapReady = hasAmapKey()
const mapError = ref('')
let mapInstance = null
let nearbyPollTimer = null
let trackPollTimer = null
let vehicleMarkers = []
let trackVehicleMarker = null

const abortVisible = ref(false)
const abortReason = ref('')
const abortError = ref('')

const statusLabels = {
  PENDING: '待派单',
  DISPATCHED: '已派单',
  ACCEPTED: '已接单',
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
  if (status === 'PENDING' || status === 'DISPATCHED' || status === 'ACCEPTED') return 'badge-info'
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

function starOn(score, n) {
  return Number(score) >= n
}

function formatDistance(meters) {
  if (meters == null || Number.isNaN(Number(meters))) return '距离未知'
  const m = Number(meters)
  if (m < 1000) return `${Math.round(m)} m`
  return `${(m / 1000).toFixed(1)} km`
}

async function loadOrder() {
  loading.value = true
  error.value = ''
  actionError.value = ''
  editError.value = ''
  try {
    const res = await getDispatch(id.value)
    order.value = res.data
    syncEditForm()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载工单失败'
    order.value = null
  } finally {
    loading.value = false
  }
}

function syncEditForm() {
  const o = order.value
  if (!o) return
  editForm.plateNo = o.plateNo || ''
  editForm.vehicleTypeId = o.vehicleTypeId != null ? String(o.vehicleTypeId) : ''
  editForm.partyName = o.partyName || ''
  editForm.partyPhone = o.partyPhone || ''
}

async function onSaveEdit() {
  if (!order.value) return
  savingEdit.value = true
  editError.value = ''
  try {
    await updateDispatch(order.value.id, {
      accidentAddress: order.value.accidentAddress,
      longitude: order.value.longitude,
      latitude: order.value.latitude,
      rescueReason: order.value.rescueReason,
      dispatcherId: order.value.dispatcherId,
      vehicleId: order.value.vehicleId,
      rescuerId: order.value.rescuerId,
      plateNo: editForm.plateNo || null,
      vehicleTypeId: editForm.vehicleTypeId ? Number(editForm.vehicleTypeId) : null,
      partyName: editForm.partyName || null,
      partyPhone: editForm.partyPhone || null
    })
    const res = await getDispatch(id.value)
    order.value = res.data
    syncEditForm()
  } catch (e) {
    editError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    savingEdit.value = false
  }
}

function loadVehicleTypes() {
  listEnabledVehicleTypes()
    .then((res) => { vehicleTypes.value = res.data || [] })
    .catch(() => { vehicleTypes.value = [] })
}

async function loadNearby(opts = {}) {
  const { preserveSelection = false, silent = false } = opts
  if (!silent) {
    nearbyLoading.value = true
    nearby.value = []
    staleNearby.value = []
    matchedDistrict.value = null
    nearbyPollHint.value = ''
    nearbyHint.value = ''
  }
  nearbyError.value = ''
  if (!preserveSelection) {
    selectedVehicleId.value = null
  }
  try {
    if (hasCoords.value) {
      const res = await nearbyVehicles({
        lng: order.value.longitude,
        lat: order.value.latitude,
        limit: 20
      })
      matchedDistrict.value = res.data?.matchedDistrict ?? null
      nearby.value = res.data?.vehicles || []
      staleNearby.value = res.data?.staleVehicles || []
      if (!amapReady) {
        nearbyHint.value = '未配置地图 Key，已按距离排序展示附近空闲车辆。'
      }
    } else {
      nearbyHint.value = '工单无坐标，展示空闲车辆列表（无距离排序）。'
      matchedDistrict.value = null
      const res = await listVehicles({ status: 'IDLE', page: 1, size: 100 })
      const list = res.data?.list || []
      nearby.value = []
      staleNearby.value = list.slice(0, 20).map((vehicle) => ({
        vehicle,
        distanceMeters: null,
        inMatchedDistrict: false,
        locationFresh: false
      }))
    }

    const allItems = [...nearby.value, ...staleNearby.value]
    if (preserveSelection) {
      if (
        selectedVehicleId.value != null &&
        !allItems.some((i) => i.vehicle.id === selectedVehicleId.value)
      ) {
        selectedVehicleId.value = null
      }
    } else {
      const pref = order.value?.vehicleId
      if (pref != null) {
        const hit = allItems.find((i) => i.vehicle.id === pref)
        if (hit) selectedVehicleId.value = pref
        else {
          nearbyHint.value =
            (nearbyHint.value ? nearbyHint.value + ' ' : '') + '预填车辆当前不可派，请另选'
        }
      }
    }

    if (canShowMap.value) {
      syncVehicleMarkers()
    }
    if (silent) {
      nearbyPollHint.value = ''
    }
  } catch (e) {
    if (silent) {
      nearbyPollHint.value = e.response?.data?.message || e.message || '刷新附近车辆失败'
    } else {
      nearbyError.value = e.response?.data?.message || e.message || '加载附近车辆失败'
    }
  } finally {
    if (!silent) {
      nearbyLoading.value = false
    }
  }
}

function clearVehicleMarkers() {
  vehicleMarkers.forEach((m) => {
    if (m && typeof m.setMap === 'function') m.setMap(null)
  })
  vehicleMarkers = []
}

function syncVehicleMarkers() {
  if (!mapInstance || !window.AMap) return
  clearVehicleMarkers()
  const AMap = window.AMap
  const icon = createVehicleMapIcon(AMap)
  nearby.value.forEach((item) => {
    const v = item.vehicle
    if (v?.longitude == null || v?.latitude == null) return
    const marker = new AMap.Marker({
      position: [Number(v.longitude), Number(v.latitude)],
      map: mapInstance,
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
      selectedVehicleId.value = v.id
    })
    vehicleMarkers.push(marker)
  })
}

function startNearbyPoll() {
  stopNearbyPoll()
  if (!hasCoords.value || order.value?.status !== 'PENDING') return
  nearbyPollTimer = setInterval(() => {
    loadNearby({ preserveSelection: true, silent: true })
  }, 20000)
}

function stopNearbyPoll() {
  if (nearbyPollTimer) {
    clearInterval(nearbyPollTimer)
    nearbyPollTimer = null
  }
}

async function loadTrackedVehicle({ silent = false } = {}) {
  trackPollHint.value = ''
  const vehicleId = order.value?.vehicleId
  if (!vehicleId) {
    trackedVehicle.value = null
    trackHint.value = '本单未绑定车辆'
    syncTrackMarkers()
    return
  }
  try {
    const res = await getVehicle(vehicleId)
    trackedVehicle.value = res.data || null
    const v = trackedVehicle.value
    if (v && v.longitude != null && v.latitude != null) {
      trackHint.value = v.plateNo ? `施救车辆：${v.plateNo}` : ''
    } else {
      trackHint.value = '车辆暂无位置'
    }
    syncTrackMarkers()
  } catch (e) {
    if (silent) {
      trackPollHint.value = e.response?.data?.message || e.message || '刷新车辆位置失败'
    } else {
      trackHint.value = e.response?.data?.message || e.message || '加载车辆位置失败'
      trackedVehicle.value = null
    }
  }
}

function startTrackPoll() {
  stopTrackPoll()
  if (order.value?.status === 'PENDING' || !order.value?.vehicleId) return
  trackPollTimer = setInterval(() => {
    loadTrackedVehicle({ silent: true })
  }, 20000)
}

function stopTrackPoll() {
  if (trackPollTimer) {
    clearInterval(trackPollTimer)
    trackPollTimer = null
  }
}

function clearTrackVehicleMarker() {
  if (trackVehicleMarker && typeof trackVehicleMarker.setMap === 'function') {
    trackVehicleMarker.setMap(null)
  }
  trackVehicleMarker = null
}

function syncTrackMarkers() {
  if (!mapInstance || !window.AMap) return
  clearTrackVehicleMarker()
  const v = trackedVehicle.value
  if (v?.longitude == null || v?.latitude == null) return
  const AMap = window.AMap
  const icon = createVehicleMapIcon(AMap)
  trackVehicleMarker = new AMap.Marker({
    position: [Number(v.longitude), Number(v.latitude)],
    map: mapInstance,
    icon,
    offset: new AMap.Pixel(-22, -18),
    title: v.plateNo || '',
    label: {
      content: v.plateNo || '施救车',
      direction: 'top',
      offset: new AMap.Pixel(0, -4)
    }
  })
  const accident = [Number(order.value.longitude), Number(order.value.latitude)]
  mapInstance.setFitView(
    [new AMap.Marker({ position: accident }), trackVehicleMarker],
    false,
    [60, 60, 60, 60]
  )
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
    syncVehicleMarkers()
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

async function initTrackMap() {
  destroyMap()
  mapError.value = ''
  if (!canShowMap.value || !trackMapEl.value) return
  try {
    const AMap = await loadAmap()
    const lng = Number(order.value.longitude)
    const lat = Number(order.value.latitude)
    mapInstance = new AMap.Map(trackMapEl.value, {
      zoom: 14,
      center: [lng, lat]
    })
    new AMap.Marker({ position: [lng, lat], map: mapInstance })
    syncTrackMarkers()
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

function destroyMap() {
  clearVehicleMarkers()
  clearTrackVehicleMarker()
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
    const picked = [...nearby.value, ...staleNearby.value].find(
      (item) => item.vehicle?.id === selectedVehicleId.value
    )
    const res = await assignDispatch(id.value, {
      vehicleId: selectedVehicleId.value,
      rescuerId: picked?.vehicle?.driverUserId ?? null
    })
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
    stopNearbyPoll()
    stopTrackPoll()
    trackedVehicle.value = null
    trackHint.value = ''
    trackPollHint.value = ''
    if (!status) return
    if (status === 'PENDING') {
      await loadNearby()
      await nextTick()
      await initMap()
      startNearbyPoll()
    } else {
      await loadTrackedVehicle()
      await nextTick()
      await initTrackMap()
      startTrackPoll()
    }
  }
)

watch(
  () => route.params.id,
  async () => {
    destroyMap()
    stopNearbyPoll()
    stopTrackPoll()
    trackedVehicle.value = null
    trackHint.value = ''
    trackPollHint.value = ''
    nearby.value = []
    staleNearby.value = []
    matchedDistrict.value = null
    selectedVehicleId.value = null
    nearbyPollHint.value = ''
    await loadOrder()
    // Status watcher may not re-fire when both orders share the same status
    const status = order.value?.status
    if (!status) return
    if (status === 'PENDING') {
      await loadNearby()
      await nextTick()
      await initMap()
      startNearbyPoll()
    } else {
      await loadTrackedVehicle()
      await nextTick()
      await initTrackMap()
      startTrackPoll()
    }
  }
)

onMounted(async () => {
  loadVehicleTypes()
  await loadOrder()
})

onBeforeUnmount(() => {
  stopNearbyPoll()
  stopTrackPoll()
  destroyMap()
})
</script>

<style scoped>
.loading-text {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.detail-layout {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 1rem;
  align-items: start;
  grid-template-areas: "left right";
}

.detail-left,
.detail-right {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.detail-left {
  grid-area: left;
}

.detail-right {
  grid-area: right;
  position: sticky;
  top: 1rem;
}

.detail-layout.rated-layout {
  align-items: stretch;
  grid-template-areas:
    "info map"
    "scene eval"
    "note .";
}

.detail-layout.rated-layout:not(:has(.scene-panel)) {
  grid-template-areas:
    "info map"
    "note eval";
}

.rated-layout .detail-left,
.rated-layout .detail-right {
  display: contents;
}

.rated-layout .order-info {
  grid-area: info;
}

.rated-layout .map-panel {
  grid-area: map;
  display: flex;
  flex-direction: column;
}

.rated-layout .scene-panel {
  grid-area: scene;
}

.rated-layout .eval-panel {
  grid-area: eval;
}

.rated-layout .action-panel {
  grid-area: note;
}

.rated-layout .map-box,
.rated-layout .map-placeholder {
  flex: 1;
  height: auto;
  min-height: 280px;
}

.info-panel {
  padding: 1.15rem 1.25rem;
  margin-bottom: 0;
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

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  margin: 0 0 0.85rem;
}

.scene-panel .section-title,
.eval-panel .section-title {
  margin-bottom: 0.85rem;
}

.eval-panel {
  margin-bottom: 0;
}

.stars {
  display: flex;
  gap: 0.15rem;
  font-size: 1.15rem;
  line-height: 1;
  color: #d4dbe6;
  letter-spacing: 0.04em;
}

.star.on {
  color: #f5a623;
}

.scene-panel .photo-grid {
  margin-top: 0.15rem;
}

.subsection-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin: 0.75rem 0 0.5rem;
}

.subsection-title {
  font-size: 0.85rem;
  font-weight: 600;
  margin: 0;
  color: var(--text);
}

.vehicle-section:first-of-type .subsection-header {
  margin-top: 0;
}

.toggle-stale {
  padding: 0.2rem 0.55rem;
  font-size: 0.75rem;
}

.vehicle-section + .vehicle-section {
  margin-top: 0.35rem;
}

.map-panel,
.vehicle-panel,
.action-panel,
.edit-panel {
  padding: 1.1rem 1.2rem;
}

.track-map-panel,
.edit-panel,
.map-panel,
.vehicle-panel,
.action-panel,
.scene-panel,
.eval-panel {
  margin-bottom: 0;
}

.photo-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.photo-thumb {
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  width: 112px;
  height: 112px;
  background: var(--bg-muted);
  cursor: pointer;
  display: block;
  line-height: 0;
}

.photo-thumb:hover {
  background: var(--bg-muted);
}

.photo-thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-preview {
  align-items: center;
  padding: 2rem 1rem;
}

.photo-preview img {
  max-width: min(920px, 100%);
  max-height: 86vh;
  border-radius: 8px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.28);
}

.edit-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1rem;
  align-items: flex-end;
}

.edit-form label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
  min-width: 160px;
}

.edit-form input,
.edit-form select {
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  background: #fff;
}

.map-box {
  height: min(70vh, 640px);
  min-height: 480px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-muted);
}

.map-placeholder {
  min-height: 480px;
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

@media (max-width: 960px) {
  .detail-layout,
  .detail-layout.rated-layout,
  .detail-layout.rated-layout:not(:has(.scene-panel)) {
    grid-template-columns: 1fr;
    grid-template-areas:
      "info"
      "map"
      "scene"
      "eval"
      "note";
    align-items: start;
  }

  .detail-layout:not(.rated-layout) .detail-right {
    position: static;
    order: 2;
  }

  .detail-layout:not(.rated-layout) .detail-left {
    order: 1;
  }

  .map-box,
  .map-placeholder {
    min-height: 320px;
    height: 360px;
  }

  .rated-layout .map-box,
  .rated-layout .map-placeholder {
    min-height: 280px;
    height: 320px;
    flex: none;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-grid .span-2 {
    grid-column: auto;
  }
}
</style>
