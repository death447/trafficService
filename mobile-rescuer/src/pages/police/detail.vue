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
      <view class="line">派单时间：{{ formatTime(order.dispatchedAt) }}</view>
      <view class="line">接单时间：{{ formatTime(order.acceptedAt) }}</view>
      <view class="line" v-if="order.checkedInAt">
        签到（{{ order.checkinMode || '-' }}）：{{ formatTime(order.checkedInAt) }}
      </view>
      <view class="line">完成时间：{{ formatTime(order.completedAt) }}</view>
      <view class="line" v-if="order.rejectReason">退单原因：{{ order.rejectReason }}</view>
      <view class="line" v-if="order.abortReason">中止原因：{{ order.abortReason }}</view>
    </view>

    <view class="card map-card">
      <view class="section-title">位置</view>
      <div v-if="canShowMap" id="police-detail-map" class="map-box"></div>
      <view v-else class="map-placeholder">
        <text v-if="!hasAccidentCoords">暂无事故坐标，无法展示地图</text>
        <text v-else>未配置地图 Key</text>
      </view>
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

    <view class="actions" v-if="canRate">
      <view class="btn-primary" @click="goRate">评价</view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { getPoliceTask } from '../../api/police'
import { requirePageAccess } from '../../utils/guard.js'
import { canRatePoliceTask } from '../../utils/workspace.js'
import { hasAmapKey, loadAmap } from '../../utils/amap.js'
import { mediaUrl } from '../../utils/request.js'

const id = ref('')
const order = ref(null)
const fieldRecord = ref(null)
const evaluation = ref(null)
const damageMedias = ref([])
const parkMedias = ref([])
const mapError = ref('')
const amapReady = hasAmapKey()
let mapInstance = null
let accidentMarker = null

const hasAccidentCoords = computed(
  () => order.value?.longitude != null && order.value?.latitude != null
)
const canShowMap = computed(() => amapReady && hasAccidentCoords.value)
const canRate = computed(() => canRatePoliceTask(order.value?.status, !!evaluation.value))

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

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function starOn(score, n) {
  return Number(score) >= n
}

function previewMedias(list, filePath) {
  const urls = list.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: mediaUrl(filePath) })
}

function destroyMap() {
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
  accidentMarker = null
}

async function ensureMap() {
  mapError.value = ''
  if (!canShowMap.value) {
    destroyMap()
    return
  }
  await nextTick()
  const el = typeof document !== 'undefined' ? document.getElementById('police-detail-map') : null
  if (!el) return
  try {
    const AMap = await loadAmap()
    const lng = Number(order.value.longitude)
    const lat = Number(order.value.latitude)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
      mapError.value = '事故坐标无效'
      return
    }
    destroyMap()
    mapInstance = new AMap.Map(el, { zoom: 14, center: [lng, lat], resizeEnable: true })
    accidentMarker = new AMap.Marker({ position: [lng, lat] })
    mapInstance.add([accidentMarker])
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

async function load() {
  if (!id.value) return
  try {
    const res = await getPoliceTask(id.value)
    const data = res.data || {}
    order.value = data.order || null
    fieldRecord.value = data.fieldRecord || null
    evaluation.value = data.evaluation || null
    const medias = data.medias || []
    damageMedias.value = medias.filter((m) => m.bizType === 'DAMAGE')
    parkMedias.value = medias.filter((m) => m.bizType === 'PARK')
    await ensureMap()
  } catch (e) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}

function goRate() {
  uni.navigateTo({ url: `/pages/police/rate?id=${id.value}` })
}

onLoad((q) => {
  id.value = q.id
})

onShow(() => {
  if (!requirePageAccess('police')) return
  load()
})

onUnload(() => {
  destroyMap()
})
</script>

<style scoped>
.title { font-weight: 700; font-size: 32rpx; }
.status { color: #2979ff; }
.line { margin-top: 12rpx; font-size: 26rpx; }
.section-title { font-weight: 600; margin-bottom: 12rpx; }
.map-box { height: 360rpx; width: 100%; }
.map-placeholder { padding: 24rpx 0; color: #888; }
.media-grid { display: flex; flex-wrap: wrap; gap: 12rpx; }
.thumb { width: 160rpx; height: 160rpx; border-radius: 8rpx; }
.actions { margin-top: 24rpx; }
.error { color: #c62828; }
.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}
.score-label { font-size: 26rpx; }
.stars { display: flex; gap: 8rpx; font-size: 36rpx; color: #ccc; line-height: 1; }
.star.on { color: #f5a623; }
</style>
