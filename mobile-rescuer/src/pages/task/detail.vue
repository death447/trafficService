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
    </view>

    <view class="actions" v-if="order.status === 'DISPATCHED'">
      <view class="btn-primary" @click="onAccept">接单</view>
      <view class="btn-danger" @click="promptReject">退单</view>
    </view>

    <view class="actions" v-if="order.status === 'ACCEPTED'">
      <view v-if="!order.checkedInAt" class="btn-primary" @click="onCheckin">签到</view>
      <view v-if="!order.checkedInAt" class="btn-ghost" @click="promptManual">无法定位，手动签到</view>
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
  completeTask
} from '../../api/rescuer'

const id = ref(null)
const order = ref(null)
const fieldRecord = ref(null)
const reasonPanel = ref(null)
const reasonText = ref('')

const assignedVehicle = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
const mapHint = ref('')
const pollHint = ref('')
let mapInstance = null
let vehicleMarker = null
let pollTimer = null
let pageVisible = false

const hasAccidentCoords = computed(() =>
  order.value?.longitude != null && order.value?.latitude != null
)
const canShowMap = computed(() => amapReady && hasAccidentCoords.value)

onLoad((q) => {
  id.value = q.id
})

onShow(() => {
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

async function load({ silent = false } = {}) {
  try {
    const res = await getTask(id.value)
    order.value = res.data?.order || null
    fieldRecord.value = res.data?.fieldRecord || null
    assignedVehicle.value = res.data?.assignedVehicle || null
    pollHint.value = ''
    updateMapHint()
    await nextTick()
    await ensureMap()
    syncVehicleMarker()
  } catch (_) {
    // Poll / refresh: keep last good order + map; only wipe on true initial failure
    if (silent || order.value) {
      if (silent) pollHint.value = '位置刷新失败，显示上次数据'
      return
    }
    destroyMap()
    order.value = null
    fieldRecord.value = null
    assignedVehicle.value = null
  }
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
      new AMap.Marker({ position: [lng, lat], map: mapInstance })
      // Force layout after flex/card paint
      setTimeout(() => {
        if (mapInstance && typeof mapInstance.resize === 'function') {
          mapInstance.resize()
        }
      }, 100)
    }
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

function syncVehicleMarker() {
  if (!mapInstance || !window.AMap) return
  if (vehicleMarker) {
    vehicleMarker.setMap(null)
    vehicleMarker = null
  }
  const v = assignedVehicle.value
  if (v?.longitude == null || v?.latitude == null) return
  const AMap = window.AMap
  const icon = createVehicleMapIcon(AMap)
  vehicleMarker = new AMap.Marker({
    position: [Number(v.longitude), Number(v.latitude)],
    map: mapInstance,
    icon,
    offset: new AMap.Pixel(-22, -18),
    title: v.plateNo || '',
    label: {
      content: v.plateNo || '救援车',
      direction: 'top',
      offset: new AMap.Pixel(0, -4)
    }
  })
  const accident = [Number(order.value.longitude), Number(order.value.latitude)]
  mapInstance.setFitView(
    [new AMap.Marker({ position: accident }), vehicleMarker],
    false,
    [40, 40, 40, 40]
  )
}

function destroyMap() {
  if (vehicleMarker) {
    vehicleMarker.setMap(null)
    vehicleMarker = null
  }
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
  }, 20000)
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

function onCheckin() {
  uni.getLocation({
    type: 'gcj02',
    success: async (loc) => {
      try {
        await checkinTask(id.value, {
          lng: loc.longitude,
          lat: loc.latitude,
          mode: 'AUTO'
        })
        uni.showToast({ title: '签到成功', icon: 'success' })
        load()
      } catch (_) {
        // Distance / API errors already toasted by request.js; do not open MANUAL
      }
    },
    fail: () => promptManual()
  })
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
</style>
