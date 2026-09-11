<template>
  <view class="page" v-if="order">
    <view class="card">
      <view class="row-between">
        <text class="title">{{ order.orderNo || ('#' + order.id) }}</text>
        <text class="status">{{ statusText(order.status) }}</text>
      </view>
      <view class="line">事故地址：{{ order.accidentAddress || '-' }}</view>
      <view class="line">救援事由：{{ order.rescueReason || '-' }}</view>
      <view class="line">事故车牌：{{ order.plateNo || '-' }}</view>
      <view class="line">事故联系人：{{ order.partyName || '-' }}</view>
      <view class="line">联系方式：{{ order.partyPhone || '-' }}</view>
      <view class="line muted" v-if="order.checkedInAt">
        已签到（{{ order.checkinMode }}）{{ order.checkedInAt }}
      </view>
      <view class="line muted" v-if="order.rejectReason">退单原因：{{ order.rejectReason }}</view>
    </view>

    <view class="card map-card">
      <view class="section-title">位置</view>
      <!-- H5: AMap requires a native HTMLElement (div), not uni-view -->
      <div v-if="canShowMap" id="task-detail-map" class="map-box"></div>
      <view v-else class="map-placeholder">
        <text v-if="!hasAccidentCoords">暂无事故坐标，无法展示地图</text>
        <text v-else-if="!amapReady">未配置地图 Key</text>
      </view>
      <view v-if="mapHint" class="line muted">{{ mapHint }}</view>
      <view v-if="pollHint" class="line muted">{{ pollHint }}</view>
      <view v-if="mapError" class="line error">{{ mapError }}</view>
    </view>

    <view class="card" v-if="fieldRecord">
      <view class="section-title">现场摘要</view>
      <view class="line">车牌：{{ fieldRecord.plateNo || '-' }}</view>
      <view class="line">车型：{{ fieldRecord.vehicleType || '-' }}</view>
      <view class="line">受损：{{ fieldRecord.damageDesc || '-' }}</view>
      <view class="line">停放：{{ fieldRecord.parkAddress || '-' }}</view>

      <view v-if="damageMedias.length || parkMedias.length" class="media-block">
        <view v-if="damageMedias.length" class="media-section">
          <view class="section-title media-title">受损照片</view>
          <view class="media-grid">
            <image
              v-for="m in damageMedias"
              :key="m.id"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewMedias(damageMedias, m.filePath)"
            />
          </view>
        </view>
        <view v-if="parkMedias.length" class="media-section">
          <view class="section-title media-title">停放照片</view>
          <view class="media-grid">
            <image
              v-for="m in parkMedias"
              :key="m.id"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewMedias(parkMedias, m.filePath)"
            />
          </view>
        </view>
      </view>
    </view>

    <view class="card" v-if="evaluation">
      <view class="section-title">评价</view>
      <view class="score-row">
        <text class="score-label">到达及时</text>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="'p' + n"
            class="star"
            :class="{ on: starOn(evaluation.scorePunctual, n) }"
          >★</text>
        </view>
      </view>
      <view class="score-row">
        <text class="score-label">处置规范</text>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="'st' + n"
            class="star"
            :class="{ on: starOn(evaluation.scoreStandard, n) }"
          >★</text>
        </view>
      </view>
      <view class="score-row">
        <text class="score-label">操作安全</text>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="'sa' + n"
            class="star"
            :class="{ on: starOn(evaluation.scoreSafety, n) }"
          >★</text>
        </view>
      </view>
      <view class="score-row">
        <text class="score-label">服务态度</text>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="'at' + n"
            class="star"
            :class="{ on: starOn(evaluation.scoreAttitude, n) }"
          >★</text>
        </view>
      </view>
      <view class="line">意见：{{ evaluation.comment || '无' }}</view>
    </view>

    <view class="card" v-else-if="order.status === 'COMPLETED'">
      <view class="section-title">评价</view>
      <view class="line muted">暂无评价</view>
    </view>

    <view class="actions" v-if="order.status === 'DISPATCHED'">
      <view class="btn-primary" @click="onAccept">接单</view>
      <view class="btn-danger" @click="promptReject">退单</view>
    </view>

    <view class="actions" v-if="order.status === 'ACCEPTED'">
      <view v-if="!order.checkedInAt" class="btn-ghost" @click="promptManual">手动签到（需填写原因）</view>
      <view class="btn-ghost" @click="goScene">现场采集</view>
      <view class="btn-ghost" @click="goPark">入库登记</view>
      <view
        class="btn-primary"
        :class="{ 'btn-disabled': !order.checkedInAt }"
        @click="onComplete"
      >
        完成工单
      </view>
      <view v-if="!order.checkedInAt" class="btn-danger" @click="promptReject">退单</view>
    </view>

    <view class="mask" v-if="reasonPanel" @click.self="reasonPanel = null">
      <view class="card panel">
        <view class="section-title">{{ reasonPanel === 'reject' ? '退单原因' : '手动签到原因' }}</view>
        <textarea class="field-input area" v-model="reasonText" placeholder="请填写原因（必填）" />
        <view class="btn-primary" @click="submitReason">确认</view>
        <view class="btn-ghost" style="margin-top: 16rpx" @click="reasonPanel = null">取消</view>
      </view>
    </view>
  </view>
  <view v-else class="page muted center">加载中…</view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { hasAmapKey, loadAmap, createVehicleMapIcon } from '../../utils/amap'
import {
  getTask,
  acceptTask,
  rejectTask,
  checkinTask,
  completeTask,
  listMedia
} from '../../api/rescuer'
import { mediaUrl } from '../../utils/request'
import { canAutoCheckin, AUTO_CHECKIN_RADIUS_METERS } from '../../utils/geo'
import { requirePageAccess } from '../../utils/guard.js'

const id = ref(null)
const order = ref(null)
const fieldRecord = ref(null)
const evaluation = ref(null)
const damageMedias = ref([])
const parkMedias = ref([])
const reasonPanel = ref(null)
const reasonText = ref('')

const assignedVehicle = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
const mapHint = ref('')
const pollHint = ref('')
let mapInstance = null
let accidentMarker = null
let accidentCircle = null
let vehicleMarker = null
let pollTimer = null
let pageVisible = false
let autoCheckinInFlight = false
let autoCheckinDone = false

const hasAccidentCoords = computed(() =>
  order.value?.longitude != null && order.value?.latitude != null
)
const canShowMap = computed(() => amapReady && hasAccidentCoords.value)

onLoad((q) => {
  if (!requirePageAccess('rescuer')) return
  id.value = q.id
  autoCheckinDone = false
})

onShow(() => {
  if (!requirePageAccess('rescuer')) return
  pageVisible = true
  if (id.value) {
    load().then(() => {
      if (pageVisible) startPoll()
    })
  }
})

onHide(() => {
  pageVisible = false
  stopPoll()
})

onUnload(() => {
  pageVisible = false
  stopPoll()
  destroyMap()
})

function statusText(s) {
  const map = {
    PENDING: '待派单',
    DISPATCHED: '已派单',
    ACCEPTED: '已接单',
    COMPLETED: '已完成',
    ABORTED: '已中止'
  }
  return map[s] || s || '-'
}

function starOn(score, n) {
  return Number(score) >= n
}

async function load({ silent = false } = {}) {
  try {
    if (silent) {
      const res = await getTask(id.value)
      order.value = res.data?.order || null
      fieldRecord.value = res.data?.fieldRecord || null
      evaluation.value = res.data?.evaluation || null
      assignedVehicle.value = res.data?.assignedVehicle || null
      pollHint.value = ''
      updateMapHint()
      await nextTick()
      if (mapInstance) {
        syncVehicleMarker({ recenter: false })
      }
      await tryAutoCheckin()
      return
    }

    const [res, mediaRes] = await Promise.all([
      getTask(id.value),
      listMedia(id.value).catch(() => ({ data: [] }))
    ])
    order.value = res.data?.order || null
    fieldRecord.value = res.data?.fieldRecord || null
    evaluation.value = res.data?.evaluation || null
    assignedVehicle.value = res.data?.assignedVehicle || null
    const all = mediaRes.data || []
    damageMedias.value = all.filter((m) => m.bizType === 'DAMAGE')
    parkMedias.value = all.filter((m) => m.bizType === 'PARK')
    pollHint.value = ''
    updateMapHint()
    await nextTick()
    await ensureMap()
    syncVehicleMarker({ recenter: true })
    await tryAutoCheckin()
  } catch (_) {
    if (silent || order.value) {
      if (silent) pollHint.value = '位置刷新失败，显示上次数据'
      return
    }
    destroyMap()
    order.value = null
    fieldRecord.value = null
    evaluation.value = null
    assignedVehicle.value = null
    damageMedias.value = []
    parkMedias.value = []
  }
}

function previewMedias(list, filePath) {
  const urls = list.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: mediaUrl(filePath) })
}

function updateMapHint() {
  const v = assignedVehicle.value
  if (!v) {
    mapHint.value = order.value?.vehicleId ? '车辆信息暂不可用' : ''
    return
  }
  if (v.longitude == null || v.latitude == null) {
    mapHint.value = '车辆暂无位置'
  } else {
    mapHint.value = v.plateNo ? `救援车辆：${v.plateNo}` : ''
  }
}

async function ensureMap() {
  mapError.value = ''
  if (!canShowMap.value) {
    destroyMap()
    return
  }
  // Wait for v-if div to mount
  await nextTick()
  await new Promise((r) => setTimeout(r, 50))
  let el = typeof document !== 'undefined' ? document.getElementById('task-detail-map') : null
  if (!el) {
    await nextTick()
    el = document.getElementById('task-detail-map')
  }
  if (!el) {
    mapError.value = '地图容器未就绪'
    return
  }
  try {
    const AMap = await loadAmap()
    if (
      mapInstance &&
      typeof mapInstance.getContainer === 'function' &&
      mapInstance.getContainer() !== el
    ) {
      destroyMap()
    }
    if (!mapInstance) {
      const lng = Number(order.value.longitude)
      const lat = Number(order.value.latitude)
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
        mapError.value = '事故坐标无效'
        return
      }
      mapInstance = new AMap.Map(el, {
        zoom: 14,
        center: [lng, lat],
        resizeEnable: true
      })
      accidentMarker = new AMap.Marker({ position: [lng, lat] })
      // AMap 2.0: prefer map.add over constructor `map` for Circle reliability
      accidentCircle = new AMap.Circle({
        center: new AMap.LngLat(lng, lat),
        radius: AUTO_CHECKIN_RADIUS_METERS,
        strokeColor: '#e53935',
        strokeOpacity: 0.9,
        strokeWeight: 3,
        fillColor: '#e53935',
        fillOpacity: 0.22,
        zIndex: 50,
        bubble: true
      })
      mapInstance.add([accidentMarker, accidentCircle])
      // Prefer vehicle center when coords already known; else accident until sync
      centerMapView()
      // Force layout after flex/card paint
      setTimeout(() => {
        if (mapInstance && typeof mapInstance.resize === 'function') {
          mapInstance.resize()
        }
        centerMapView()
      }, 100)
    } else if (!accidentCircle && Number.isFinite(Number(order.value.longitude))) {
      // Hot reload / remount: map exists but circle missing — recreate circle
      ensureAccidentCircle(AMap)
    }
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

function ensureAccidentCircle(AMap) {
  if (!mapInstance || !order.value) return
  const lng = Number(order.value.longitude)
  const lat = Number(order.value.latitude)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
  if (!accidentMarker) {
    accidentMarker = new AMap.Marker({ position: [lng, lat] })
    mapInstance.add(accidentMarker)
  }
  if (!accidentCircle) {
    accidentCircle = new AMap.Circle({
      center: new AMap.LngLat(lng, lat),
      radius: AUTO_CHECKIN_RADIUS_METERS,
      strokeColor: '#e53935',
      strokeOpacity: 0.9,
      strokeWeight: 3,
      fillColor: '#e53935',
      fillOpacity: 0.22,
      zIndex: 50,
      bubble: true
    })
    mapInstance.add(accidentCircle)
  }
}

/** Center on rescue vehicle when available; otherwise fit accident + 500m circle. */
function centerMapView() {
  if (!mapInstance) return
  const v = assignedVehicle.value
  const vLng = Number(v?.longitude)
  const vLat = Number(v?.latitude)
  if (Number.isFinite(vLng) && Number.isFinite(vLat)) {
    const zoom = mapInstance.getZoom()
    const z = Number.isFinite(zoom) && zoom >= 12 ? zoom : 15
    mapInstance.setZoomAndCenter(z, [vLng, vLat])
    return
  }
  const overlays = [accidentMarker, accidentCircle].filter(Boolean)
  if (overlays.length) {
    mapInstance.setFitView(overlays, false, [40, 40, 40, 40])
  }
}

function syncVehicleMarker({ recenter = true } = {}) {
  if (!mapInstance || !window.AMap) return
  const AMap = window.AMap
  ensureAccidentCircle(AMap)
  const v = assignedVehicle.value
  const lng = Number(v?.longitude)
  const lat = Number(v?.latitude)
  const hasCoords = Number.isFinite(lng) && Number.isFinite(lat)

  if (!hasCoords) {
    if (vehicleMarker) {
      try {
        mapInstance.remove(vehicleMarker)
      } catch (_) {
        vehicleMarker.setMap?.(null)
      }
      vehicleMarker = null
    }
    if (recenter) centerMapView()
    return
  }

  if (vehicleMarker) {
    vehicleMarker.setPosition([lng, lat])
    if (v.plateNo) {
      vehicleMarker.setTitle?.(v.plateNo)
      vehicleMarker.setLabel?.({
        content: v.plateNo,
        direction: 'top',
        offset: new AMap.Pixel(0, -4)
      })
    }
    if (recenter) centerMapView()
    return
  }

  const icon = createVehicleMapIcon(AMap)
  vehicleMarker = new AMap.Marker({
    position: [lng, lat],
    icon,
    offset: new AMap.Pixel(-22, -18),
    title: v.plateNo || '',
    label: {
      content: v.plateNo || '救援车',
      direction: 'top',
      offset: new AMap.Pixel(0, -4)
    }
  })
  mapInstance.add(vehicleMarker)
  if (recenter) centerMapView()
}

function destroyMap() {
  if (mapInstance) {
    const overlays = [vehicleMarker, accidentCircle, accidentMarker].filter(Boolean)
    if (overlays.length && typeof mapInstance.remove === 'function') {
      try {
        mapInstance.remove(overlays)
      } catch (_) {}
    }
  }
  vehicleMarker = null
  accidentCircle = null
  accidentMarker = null
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
}

function startPoll() {
  stopPoll()
  const v = assignedVehicle.value
  const hasVehicleCoords = v && v.longitude != null && v.latitude != null
  // Spec: poll when tracking vehicle location; also refresh if vehicleId exists so coords can appear later
  if (!order.value?.vehicleId && !hasVehicleCoords) return
  pollTimer = setInterval(() => {
    load({ silent: true })
  }, 10000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function onAccept() {
  try {
    await acceptTask(id.value)
    uni.showToast({ title: '已接单', icon: 'success' })
    load()
  } catch (_) {}
}

function promptReject() {
  reasonText.value = ''
  reasonPanel.value = 'reject'
}

function promptManual() {
  reasonText.value = ''
  reasonPanel.value = 'manual'
}

async function submitReason() {
  const text = (reasonText.value || '').trim()
  if (!text) {
    uni.showToast({ title: '请填写原因', icon: 'none' })
    return
  }
  const mode = reasonPanel.value
  reasonPanel.value = null
  if (mode === 'reject') {
    try {
      await rejectTask(id.value, text)
      uni.showToast({ title: '已退单', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 400)
    } catch (_) {}
    return
  }
  if (mode === 'manual') {
    try {
      await checkinTask(id.value, { mode: 'MANUAL', remark: text })
      uni.showToast({ title: '手动签到成功', icon: 'success' })
      load()
    } catch (_) {}
  }
}

async function tryAutoCheckin() {
  if (autoCheckinDone || autoCheckinInFlight) return
  const o = order.value
  const v = assignedVehicle.value
  if (!canAutoCheckin(o, v)) return
  autoCheckinInFlight = true
  try {
    await checkinTask(
      id.value,
      {
        lng: Number(v.longitude),
        lat: Number(v.latitude),
        mode: 'AUTO'
      },
      { showError: false }
    )
    autoCheckinDone = true
    uni.showToast({ title: '签到成功', icon: 'success' })
    await load({ silent: true })
  } catch (_) {
    // silent: AUTO during poll must not toast every 10s
  } finally {
    autoCheckinInFlight = false
  }
}

function goScene() {
  uni.navigateTo({ url: `/pages/task/scene?id=${id.value}` })
}

function goPark() {
  uni.navigateTo({ url: `/pages/task/park?id=${id.value}` })
}

async function onComplete() {
  if (!order.value?.checkedInAt) {
    uni.showToast({ title: '请先签到', icon: 'none' })
    return
  }
  try {
    await completeTask(id.value)
    uni.showToast({ title: '已完成', icon: 'success' })
    load()
  } catch (_) {}
}
</script>

<style scoped>
.title {
  font-size: 32rpx;
  font-weight: 700;
}
.status {
  color: #2979ff;
}
.line {
  margin-top: 16rpx;
  line-height: 1.5;
}
.section-title {
  font-weight: 600;
  margin-bottom: 8rpx;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.center {
  text-align: center;
  padding-top: 80rpx;
}
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  z-index: 100;
}
.panel {
  width: 100%;
  margin: 0;
}
.area {
  min-height: 140rpx;
  margin: 20rpx 0 28rpx;
}
.map-box {
  width: 100%;
  height: 240px;
  min-height: 240px;
  margin-top: 16rpx;
  border-radius: 12rpx;
  overflow: hidden;
  background: #e8eef5;
}
.map-placeholder {
  margin-top: 16rpx;
  color: #888;
  font-size: 26rpx;
}
.error {
  color: #c62828;
}
.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}
.score-label { font-size: 26rpx; }
.stars { display: flex; gap: 8rpx; font-size: 36rpx; color: #ccc; line-height: 1; }
.star.on { color: #f5a623; }
.media-block {
  margin-top: 20rpx;
}
.media-section + .media-section {
  margin-top: 20rpx;
}
.media-title {
  margin-bottom: 16rpx;
}
.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.thumb {
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background: #eee;
}
</style>
